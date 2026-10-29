package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.operaciones.backend.auth.AuthTokenStore;
import com.operaciones.backend.auth.AuthTokenStore.Session;
import com.operaciones.backend.auth.UsuarioDni;
import com.operaciones.backend.auth.UsuarioDniRepository;
import com.operaciones.backend.credit.dto.ActiveLoanResponse;
import com.operaciones.backend.credit.dto.ClientBlockRequest;
import com.operaciones.backend.credit.dto.ClientDossierResponse;
import com.operaciones.backend.credit.dto.ClientPortalResponse;
import com.operaciones.backend.credit.dto.ContractResponse;
import com.operaciones.backend.credit.dto.DueInstallmentResponse;
import com.operaciones.backend.credit.dto.InstallmentResponse;
import com.operaciones.backend.credit.dto.NotificationResponse;
import com.operaciones.backend.credit.dto.PaymentCreateRequest;
import com.operaciones.backend.credit.dto.PaymentResponse;
import com.operaciones.backend.credit.dto.ReminderRequest;
import com.operaciones.backend.mail.MailService;
import com.operaciones.backend.mail.MailTemplates;
import com.operaciones.backend.mail.AppUrlProperties;

@Service
public class LoanBookService {

    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ActiveLoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanPaymentRepository paymentRepository;
    private final LoanContractRepository contractRepository;
    private final CreditNotificationRepository notificationRepository;
    private final LoanRequestRepository requestRepository;
    private final UsuarioDniRepository dniRepository;
    private final AuthTokenStore tokenStore;
    private final CashService cashService;
    private final ClientCreditBlockRepository blockRepository;
    private final AppUrlProperties appUrls;

    public LoanBookService(
            ActiveLoanRepository loanRepository,
            LoanInstallmentRepository installmentRepository,
            LoanPaymentRepository paymentRepository,
            LoanContractRepository contractRepository,
            CreditNotificationRepository notificationRepository,
            LoanRequestRepository requestRepository,
            UsuarioDniRepository dniRepository,
            AuthTokenStore tokenStore,
            CashService cashService,
            ClientCreditBlockRepository blockRepository,
            AppUrlProperties appUrls) {
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.notificationRepository = notificationRepository;
        this.requestRepository = requestRepository;
        this.dniRepository = dniRepository;
        this.tokenStore = tokenStore;
        this.cashService = cashService;
        this.blockRepository = blockRepository;
        this.appUrls = appUrls;
    }

    @Transactional
    public ActiveLoan activateFromRequest(LoanRequest request, CreditService.Quote quote) {
        if (loanRepository.existsByDniAndEstado(request.getDni(), "VIGENTE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El cliente ya tiene un préstamo vigente");
        }
        PaymentFrequency freq = request.getFrecuenciaPago() == null
                ? PaymentFrequency.MENSUAL
                : request.getFrecuenciaPago();
        LocalDate start = LocalDate.now();
        ActiveLoan loan = new ActiveLoan();
        loan.setCodigo("PT-" + (1000 + request.getId()));
        loan.setRequestId(request.getId());
        loan.setDni(request.getDni());
        loan.setClienteNombre(request.getClienteNombre());
        loan.setMonto(request.getMonto());
        loan.setCuotas(request.getCuotas());
        loan.setFrecuencia(freq);
        loan.setTasaPeriodo(quote.monthlyRate());
        loan.setCuotaMonto(quote.installment());
        loan.setInteresTotal(quote.totalInterest());
        loan.setTotalPagar(quote.totalPay());
        loan.setSaldo(quote.totalPay());
        loan.setFechaInicio(start);
        loan.setEstado("VIGENTE");
        loan = loanRepository.save(loan);

        BigDecimal balance = request.getMonto();
        BigDecimal rate = quote.monthlyRate();
        List<LoanInstallment> rows = new ArrayList<>();
        for (int n = 1; n <= request.getCuotas(); n++) {
            LocalDate due = freq == PaymentFrequency.SEMANAL ? start.plusWeeks(n) : start.plusMonths(n);
            BigDecimal interest = balance.multiply(rate, MATH).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capital = quote.installment().subtract(interest).setScale(2, RoundingMode.HALF_UP);
            if (n == request.getCuotas()) {
                capital = balance;
                interest = quote.installment().subtract(capital).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }
            if (capital.compareTo(balance) > 0) {
                capital = balance;
            }
            balance = balance.subtract(capital).max(BigDecimal.ZERO);

            LoanInstallment row = new LoanInstallment();
            row.setLoanId(loan.getId());
            row.setNumero(n);
            row.setFechaVence(due);
            row.setMonto(quote.installment());
            row.setCapital(capital);
            row.setInteres(interest);
            row.setEstado(InstallmentStatus.PENDIENTE);
            rows.add(row);
        }
        installmentRepository.saveAll(rows);

        LoanContract contract = new LoanContract();
        contract.setLoanId(loan.getId());
        contract.setDni(loan.getDni());
        contract.setCodigo("CTR-" + loan.getCodigo());
        contract.setFirmaSimulada("Firma digital simulada · " + loan.getClienteNombre());
        contract.setContenidoHtml(buildContractHtml(loan, rows, request));
        contractRepository.save(contract);

        cashService.ensureSeed();
        cashService.record(
                "DESEMBOLSO",
                request.getMonto().negate(),
                "Desembolso " + loan.getCodigo() + " · " + loan.getClienteNombre(),
                loan.getCodigo(),
                loan.getDni());

        notify(
                loan.getDni(),
                "Préstamo aprobado",
                "Tu solicitud fue aprobada. Préstamo " + loan.getCodigo()
                        + " · cuota " + money(loan.getCuotaMonto())
                        + " · revisa tu cronograma y contrato.");
        return loan;
    }

    public void notifyRejection(LoanRequest request) {
        notify(
                request.getDni(),
                "Solicitud rechazada",
                "Tu solicitud de préstamo por S/ " + money(request.getMonto())
                        + " fue rechazada. Puedes volver a solicitar más adelante.");
    }

    public ClientPortalResponse portal(String bearer) {
        String dni = requireClientDni(bearer);
        var pending = requestRepository.findFirstByDniAndEstadoInOrderByCreadoEnDesc(
                dni,
                List.of(LoanRequestStatus.EN_EVALUACION, LoanRequestStatus.OBSERVADA));
        var approvedPending = requestRepository.findFirstByDniAndEstadoInOrderByCreadoEnDesc(
                dni, List.of(LoanRequestStatus.APROBADA, LoanRequestStatus.RECHAZADA));
        ActiveLoan vigente = loanRepository.findByDniAndEstado(dni, "VIGENTE").orElse(null);
        List<ActiveLoan> historial = loanRepository.findByDniOrderByCreadoEnDesc(dni);
        List<ActiveLoanResponse> anteriores = historial.stream()
                .filter(l -> !"VIGENTE".equals(l.getEstado()) || (vigente != null && !l.getId().equals(vigente.getId())))
                .map(this::toLoan)
                .toList();
        List<PaymentResponse> pagos = paymentRepository.findByDniOrderByCreadoEnDesc(dni).stream()
                .map(this::refreshVoucherDesign)
                .map(this::toPayment)
                .toList();
        ContractResponse contrato = null;
        if (vigente != null) {
            contrato = contractRepository.findByLoanId(vigente.getId())
                    .map(c -> toContract(refreshContractDesign(vigente, c)))
                    .orElse(null);
        }
        boolean puede = vigente == null
                && !isBlocked(dni)
                && !requestRepository.existsByDniAndEstadoIn(
                        dni, List.of(LoanRequestStatus.EN_EVALUACION, LoanRequestStatus.OBSERVADA));
        LoanRequestResponseWrapper solicitud = pending
                .map(r -> new LoanRequestResponseWrapper(r))
                .or(() -> approvedPending.filter(r -> vigente == null).map(LoanRequestResponseWrapper::new))
                .orElse(null);
        return new ClientPortalResponse(
                dni,
                solicitud == null ? null : solicitud.response(),
                vigente == null ? null : toLoan(vigente),
                anteriores,
                pagos,
                contrato,
                puede);
    }

    public ActiveLoanResponse myLoan(String bearer) {
        String dni = requireClientDni(bearer);
        ActiveLoan loan = loanRepository.findByDniAndEstado(dni, "VIGENTE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tienes un préstamo vigente"));
        return toLoan(loan);
    }

    public List<InstallmentResponse> mySchedule(String bearer) {
        return myLoan(bearer).cronograma();
    }

    @Transactional
    public PaymentResponse pay(String bearer, PaymentCreateRequest request) {
        String dni = requireClientDni(bearer);
        ActiveLoan loan = loanRepository.findByDniAndEstado(dni, "VIGENTE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tienes un préstamo vigente"));
        return applyPayment(loan, request);
    }

    public List<PaymentResponse> myPayments(String bearer) {
        String dni = requireClientDni(bearer);
        return paymentRepository.findByDniOrderByCreadoEnDesc(dni).stream().map(this::toPayment).toList();
    }

    public ContractResponse myContract(String bearer) {
        String dni = requireClientDni(bearer);
        ActiveLoan loan = loanRepository.findByDniAndEstado(dni, "VIGENTE")
                .orElseGet(() -> loanRepository.findByDniOrderByCreadoEnDesc(dni).stream().findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay contrato")));
        return contractRepository.findByLoanId(loan.getId())
                .map(this::toContract)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato no encontrado"));
    }

    public List<PaymentResponse> myVouchers(String bearer) {
        return myPayments(bearer);
    }

    public List<NotificationResponse> myNotifications(String bearer) {
        String dni = requireClientDni(bearer);
        return notificationRepository.findByDniOrderByCreadoEnDesc(dni).stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getTitulo(),
                        n.getMensaje(),
                        n.isLeida(),
                        n.getCreadoEn().toString()))
                .toList();
    }

    public long unreadCount(String bearer) {
        String dni = requireClientDni(bearer);
        return notificationRepository.countByDniAndLeidaFalse(dni);
    }

    @Transactional
    public void markNotificationsRead(String bearer) {
        String dni = requireClientDni(bearer);
        List<CreditNotification> list = notificationRepository.findByDniOrderByCreadoEnDesc(dni);
        for (CreditNotification n : list) {
            n.setLeida(true);
        }
        notificationRepository.saveAll(list);
    }

    public String requireClientDni(String bearer) {
        Session session = requireSession(bearer);
        if (!"dni".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo clientes con DNI");
        }
        UsuarioDni user = dniRepository.findById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        return user.getDni();
    }

    public Session requireSession(String bearer) {
        return tokenStore.findSession(extractBearer(bearer))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }

    private void notify(String dni, String titulo, String mensaje) {
        CreditNotification n = new CreditNotification();
        n.setDni(dni);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        notificationRepository.save(n);
    }

    public List<ActiveLoanResponse> listLoans(String bearer, String estado) {
        requireStaff(bearer);
        List<ActiveLoan> rows;
        if (estado == null || estado.isBlank() || "TODOS".equalsIgnoreCase(estado)) {
            rows = loanRepository.findAllByOrderByCreadoEnDesc();
        } else {
            rows = loanRepository.findByEstadoOrderByCreadoEnDesc(estado.trim().toUpperCase(Locale.ROOT));
        }
        return rows.stream().map(this::toLoan).toList();
    }

    public ActiveLoanResponse getLoan(String bearer, Long id) {
        requireStaff(bearer);
        ActiveLoan loan = loanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        return toLoan(loan);
    }

    @Transactional
    public ActiveLoanResponse shiftSchedule(String bearer, Long loanId, String fechaInicioRaw) {
        requireStaff(bearer);
        ActiveLoan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        if (!"VIGENTE".equals(loan.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se edita cronograma de vigentes");
        }
        LocalDate start = parseIsoDate(fechaInicioRaw);
        loan.setFechaInicio(start);
        loanRepository.save(loan);
        List<LoanInstallment> rows = installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId());
        for (LoanInstallment row : rows) {
            if (row.getEstado() == InstallmentStatus.PAGADA) {
                continue;
            }
            LocalDate due = loan.getFrecuencia() == PaymentFrequency.SEMANAL
                    ? start.plusWeeks(row.getNumero())
                    : start.plusMonths(row.getNumero());
            row.setFechaVence(due);
            if (due.isBefore(LocalDate.now()) && row.getEstado() != InstallmentStatus.PAGADA) {
                row.setEstado(InstallmentStatus.VENCIDA);
            } else if (row.getEstado() == InstallmentStatus.VENCIDA && !due.isBefore(LocalDate.now())) {
                row.setEstado(InstallmentStatus.PENDIENTE);
            }
        }
        installmentRepository.saveAll(rows);
        notify(
                loan.getDni(),
                "Cronograma actualizado",
                "Tu día de pago de " + loan.getCodigo() + " inicia el " + start.format(DATE_FMT)
                        + ". Revisa tus nuevas fechas de cuota.");
        return toLoan(loan);
    }

    @Transactional
    public PaymentResponse staffPay(String bearer, Long loanId, PaymentCreateRequest request) {
        requireStaff(bearer);
        ActiveLoan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        if (!"VIGENTE".equals(loan.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El préstamo no está vigente");
        }
        return applyPayment(loan, request);
    }

    public List<DueInstallmentResponse> dueInstallments(String bearer) {
        requireStaff(bearer);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(14);
        List<DueInstallmentResponse> out = new ArrayList<>();
        for (ActiveLoan loan : loanRepository.findByEstadoOrderByCreadoEnDesc("VIGENTE")) {
            for (LoanInstallment i : installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId())) {
                if (i.getEstado() == InstallmentStatus.PAGADA) {
                    continue;
                }
                LocalDate due = i.getFechaVence();
                boolean overdue = due.isBefore(today);
                boolean upcoming = !due.isAfter(horizon);
                if (!overdue && !upcoming) {
                    continue;
                }
                if (overdue && i.getEstado() != InstallmentStatus.VENCIDA) {
                    i.setEstado(InstallmentStatus.VENCIDA);
                    installmentRepository.save(i);
                }
                int days = overdue ? (int) java.time.temporal.ChronoUnit.DAYS.between(due, today) : 0;
                out.add(new DueInstallmentResponse(
                        i.getId(),
                        loan.getId(),
                        loan.getCodigo(),
                        loan.getDni(),
                        loan.getClienteNombre(),
                        i.getNumero(),
                        due.format(DATE_FMT),
                        money(i.getMonto()),
                        overdue ? "VENCIDA" : "POR_VENCER",
                        days));
            }
        }
        out.sort((a, b) -> Integer.compare(b.diasAtraso(), a.diasAtraso()));
        return out;
    }

    @Transactional
    public Map<String, Object> sendReminder(String bearer, ReminderRequest request, MailService mailService) {
        requireStaff(bearer);
        ActiveLoan loan = loanRepository.findById(request.loanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        String mensaje = blank(request.mensaje());
        if (mensaje.isBlank()) {
            mensaje = "Hola " + loan.getClienteNombre()
                    + ", recuerda que tienes una cuota pendiente de tu préstamo "
                    + loan.getCodigo() + ". Paga a tiempo con FinanTech S.A.";
        }
        notify(loan.getDni(), "Recordatorio de pago", mensaje);
        boolean mailed = false;
        String email = blank(request.email());
        if (email.isBlank()) {
            email = dniRepository.findByDni(loan.getDni()).map(UsuarioDni::getCorreo).orElse("");
            if (email == null) {
                email = "";
            }
        }
        if (!email.isBlank() && mailService != null && mailService.isEnabled()) {
            String cuotaLabel = request.installmentId() == null
                    ? "Cuota pendiente"
                    : "Cuota #" + request.installmentId();
            String monto = money(loan.getCuotaMonto());
            if (request.installmentId() != null) {
                monto = installmentRepository.findById(request.installmentId())
                        .map(i -> money(i.getMonto()))
                        .orElse(monto);
                cuotaLabel = installmentRepository.findById(request.installmentId())
                        .map(i -> "#" + i.getNumero() + " · vence " + i.getFechaVence().format(DATE_FMT))
                        .orElse(cuotaLabel);
            }
            mailed = mailService.sendHtml(
                    email,
                    "FinanTech S.A. · Recordatorio de cuota",
                    MailTemplates.paymentReminder(
                            loan.getClienteNombre(),
                            loan.getCodigo(),
                            cuotaLabel,
                            "S/ " + monto,
                            appUrls.absoluteFrontend("/pagos/cuotas-cliente")));
        }
        return Map.of(
                "status", "ok",
                "notificado", true,
                "correoEnviado", mailed);
    }

    @Transactional
    public Map<String, Object> blockClient(String bearer, ClientBlockRequest request) {
        requireStaff(bearer);
        String dni = requireDni8(request.dni());
        LocalDate hasta = parseIsoDate(request.hasta());
        if (!hasta.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de bloqueo debe ser futura");
        }
        ClientCreditBlock block = blockRepository.findByDni(dni).orElseGet(ClientCreditBlock::new);
        block.setDni(dni);
        block.setHasta(hasta);
        block.setMotivo(blank(request.motivo()).isBlank() ? "Bloqueo temporal de nuevos préstamos" : blank(request.motivo()));
        blockRepository.save(block);
        notify(
                dni,
                "Préstamos temporalmente deshabilitados",
                "No podrás solicitar un nuevo préstamo hasta el " + hasta.format(DATE_FMT)
                        + ". Motivo: " + block.getMotivo());
        return Map.of("status", "ok", "dni", dni, "hasta", hasta.toString());
    }

    @Transactional
    public Map<String, Object> unblockClient(String bearer, String dni) {
        requireStaff(bearer);
        blockRepository.deleteByDni(requireDni8(dni));
        return Map.of("status", "ok", "dni", dni);
    }

    public boolean isBlocked(String dni) {
        return blockRepository.findByDni(dni)
                .filter(b -> !b.getHasta().isBefore(LocalDate.now()))
                .isPresent();
    }

    public Optional<ClientCreditBlock> activeBlock(String dni) {
        return blockRepository.findByDni(dni)
                .filter(b -> !b.getHasta().isBefore(LocalDate.now()));
    }

    public ClientDossierResponse dossier(String bearer, String dniRaw) {
        requireStaff(bearer);
        String dni = requireDni8(dniRaw);
        UsuarioDni user = dniRepository.findByDni(dni).orElse(null);
        List<ActiveLoan> loans = loanRepository.findByDniOrderByCreadoEnDesc(dni);
        ActiveLoan vigente = loans.stream().filter(l -> "VIGENTE".equals(l.getEstado())).findFirst().orElse(null);
        List<LoanRequest> solicitudes = requestRepository.findByDniOrderByCreadoEnDesc(dni);
        List<LoanPayment> pagos = paymentRepository.findByDniOrderByCreadoEnDesc(dni);
        int late = 0;
        for (ActiveLoan loan : loans) {
            for (LoanInstallment i : installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId())) {
                if (i.getEstado() == InstallmentStatus.PAGADA && i.getPagadoEn() != null
                        && i.getPagadoEn().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(i.getFechaVence())) {
                    late++;
                } else if (i.getEstado() == InstallmentStatus.VENCIDA) {
                    late++;
                }
            }
        }
        var block = activeBlock(dni);
        String nombre = user != null ? user.getNombres() : (vigente != null ? vigente.getClienteNombre() : dni);
        return new ClientDossierResponse(
                dni,
                nombre,
                user == null ? null : user.getTelefono(),
                user == null || user.isActivo(),
                block.map(b -> b.getHasta().toString()).orElse(null),
                block.map(ClientCreditBlock::getMotivo).orElse(null),
                loans.size(),
                (int) loans.stream().filter(l -> "VIGENTE".equals(l.getEstado())).count(),
                late,
                vigente == null ? null : toLoan(vigente),
                loans.stream().map(this::toLoan).toList(),
                pagos.stream().map(this::toPayment).toList(),
                solicitudes.stream().map(r -> new LoanRequestResponseWrapper(r).response()).toList());
    }

    public List<NotificationResponse> staffNotifications(String bearer) {
        requireStaff(bearer);
        return notificationRepository.findAllByOrderByCreadoEnDesc().stream()
                .limit(100)
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getTitulo(),
                        n.getMensaje() + " · DNI " + n.getDni(),
                        n.isLeida(),
                        n.getCreadoEn().toString()))
                .toList();
    }

    private PaymentResponse applyPayment(ActiveLoan loan, PaymentCreateRequest request) {
        String dni = loan.getDni();
        String medio = requireText(request.medio(), "Indica el medio de pago");
        String modo = request.modo() == null ? "CUOTA" : request.modo().trim().toUpperCase(Locale.ROOT);
        List<LoanInstallment> pending = installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId()).stream()
                .filter(i -> i.getEstado() != InstallmentStatus.PAGADA)
                .toList();
        if (pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay cuotas pendientes");
        }
        List<LoanInstallment> toPay = new ArrayList<>();
        if ("TODO".equals(modo) || "COMPLETO".equals(modo)) {
            toPay.addAll(pending);
        } else {
            LoanInstallment target;
            if (request.installmentId() != null) {
                target = pending.stream()
                        .filter(i -> i.getId().equals(request.installmentId()))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuota no válida"));
            } else {
                target = pending.get(0);
            }
            toPay.add(target);
        }
        BigDecimal total = BigDecimal.ZERO;
        List<String> nums = new ArrayList<>();
        Instant now = Instant.now();
        for (LoanInstallment row : toPay) {
            row.setEstado(InstallmentStatus.PAGADA);
            row.setPagadoEn(now);
            total = total.add(row.getMonto());
            nums.add(String.valueOf(row.getNumero()));
        }
        installmentRepository.saveAll(toPay);
        BigDecimal saldo = loan.getSaldo().subtract(total).max(BigDecimal.ZERO);
        loan.setSaldo(saldo);
        boolean allPaid = installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId()).stream()
                .allMatch(i -> i.getEstado() == InstallmentStatus.PAGADA);
        if (allPaid) {
            loan.setEstado("LIQUIDADO");
            loan.setSaldo(BigDecimal.ZERO);
        }
        loanRepository.save(loan);

        LoanPayment payment = new LoanPayment();
        payment.setLoanId(loan.getId());
        payment.setDni(dni);
        payment.setCodigoVoucher("VC-" + System.currentTimeMillis());
        payment.setMonto(total);
        payment.setMedio(medio);
        payment.setDetalleMedio(blank(request.detalleMedio()));
        String cuotasLabel = formatCuotasLabel(nums);
        payment.setCuotasCubiertas(cuotasLabel);
        payment.setVoucherHtml(buildVoucherHtml(loan, payment, List.of(cuotasLabel)));
        payment = paymentRepository.save(payment);

        cashService.ensureSeed();
        cashService.record(
                "COBRO",
                total,
                "Cobro " + loan.getCodigo() + " · cuotas " + cuotasLabel,
                payment.getCodigoVoucher(),
                dni);

        notify(
                dni,
                "Pago registrado",
                "Se registró un pago de " + money(total) + " (" + medio + ") · voucher "
                        + payment.getCodigoVoucher());
        return toPayment(payment);
    }

    private void requireStaff(String bearer) {
        Session session = requireSession(bearer);
        if (!"empresa".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo personal de FinanTech S.A.");
        }
    }

    private static LocalDate parseIsoDate(String raw) {
        String value = requireText(raw, "Indica la fecha (YYYY-MM-DD)");
        try {
            if (value.contains("/")) {
                return LocalDate.parse(value, DATE_FMT);
            }
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha inválida. Usa YYYY-MM-DD");
        }
    }

    private static String requireDni8(String dni) {
        String value = requireText(dni, "Indica el DNI").replaceAll("\\D", "");
        if (value.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        }
        return value;
    }

    private ActiveLoanResponse toLoan(ActiveLoan loan) {
        List<LoanInstallment> rows = installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId());
        int paid = (int) rows.stream().filter(i -> i.getEstado() == InstallmentStatus.PAGADA).count();
        int pending = loan.getCuotas() - paid;
        int progress = loan.getCuotas() <= 0 ? 0 : (int) Math.round((paid * 100.0) / loan.getCuotas());
        boolean mora = rows.stream().anyMatch(i ->
                i.getEstado() == InstallmentStatus.VENCIDA
                        || (i.getEstado() != InstallmentStatus.PAGADA && i.getFechaVence().isBefore(LocalDate.now())));
        LoanInstallment next = rows.stream()
                .filter(i -> i.getEstado() != InstallmentStatus.PAGADA)
                .findFirst()
                .orElse(null);
        String diaPago;
        if (loan.getFrecuencia() == PaymentFrequency.SEMANAL) {
            diaPago = "Cada " + loan.getFechaInicio().getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL, new Locale("es", "PE"));
        } else {
            diaPago = "Cada " + loan.getFechaInicio().getDayOfMonth() + " del mes";
        }
        List<InstallmentResponse> cronograma = rows.stream()
                .map(i -> new InstallmentResponse(
                        i.getId(),
                        i.getNumero(),
                        i.getFechaVence().format(DATE_FMT),
                        money(i.getMonto()),
                        money(i.getCapital()),
                        money(i.getInteres()),
                        i.getEstado() == InstallmentStatus.PENDIENTE && i.getFechaVence().isBefore(LocalDate.now())
                                ? InstallmentStatus.VENCIDA.name()
                                : i.getEstado().name(),
                        i.getPagadoEn() == null ? null : i.getPagadoEn().toString()))
                .toList();
        return new ActiveLoanResponse(
                loan.getId(),
                loan.getCodigo(),
                loan.getDni(),
                loan.getClienteNombre(),
                money(loan.getMonto()),
                loan.getCuotas(),
                loan.getFrecuencia().name(),
                plain(loan.getTasaPeriodo().multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP)),
                money(loan.getCuotaMonto()),
                money(loan.getInteresTotal()),
                money(loan.getTotalPagar()),
                money(loan.getSaldo()),
                loan.getFechaInicio().format(DATE_FMT),
                mora && "VIGENTE".equals(loan.getEstado()) ? "EN_MORA" : loan.getEstado(),
                paid,
                pending,
                progress,
                diaPago,
                next == null ? null : next.getFechaVence().format(DATE_FMT),
                mora,
                cronograma);
    }

    private PaymentResponse toPayment(LoanPayment p) {
        return new PaymentResponse(
                p.getId(),
                p.getLoanId(),
                p.getCodigoVoucher(),
                money(p.getMonto()),
                p.getMedio(),
                p.getDetalleMedio(),
                p.getCuotasCubiertas(),
                p.getVoucherHtml(),
                p.getCreadoEn().toString());
    }

    private ContractResponse toContract(LoanContract c) {
        return new ContractResponse(
                c.getId(),
                c.getLoanId(),
                c.getCodigo(),
                c.getFirmaSimulada(),
                c.getContenidoHtml(),
                c.getCreadoEn().toString());
    }

    private LoanContract refreshContractDesign(ActiveLoan loan, LoanContract contract) {
        String html = contract.getContenidoHtml();
        if (html != null && html.contains("class='doc'")) {
            return contract;
        }
        List<LoanInstallment> rows = installmentRepository.findByLoanIdOrderByNumeroAsc(loan.getId());
        LoanRequest request = loan.getRequestId() == null
                ? null
                : requestRepository.findById(loan.getRequestId()).orElse(null);
        contract.setContenidoHtml(buildContractHtml(loan, rows, request));
        return contractRepository.save(contract);
    }

    private LoanPayment refreshVoucherDesign(LoanPayment payment) {
        String html = payment.getVoucherHtml();
        if (html != null && html.contains("class='doc'")) {
            return payment;
        }
        ActiveLoan loan = loanRepository.findById(payment.getLoanId()).orElse(null);
        if (loan == null) {
            return payment;
        }
        List<String> nums = Arrays.stream(blank(payment.getCuotasCubiertas()).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        payment.setVoucherHtml(buildVoucherHtml(loan, payment, nums));
        return paymentRepository.save(payment);
    }

    private String buildContractHtml(ActiveLoan loan, List<LoanInstallment> rows, LoanRequest request) {
        String firma = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='utf-8'><title>Contrato ")
                .append(loan.getCodigo())
                .append("</title><style>")
                .append("*{box-sizing:border-box}html,body{margin:0;padding:0}")
                .append("body{font-family:'Segoe UI',Arial,sans-serif;color:#0a2540;background:#fff}")
                .append(".doc{width:100%;background:#fff}")
                .append(".hero{background:linear-gradient(135deg,#0a2540,#1d4ed8);color:#fff;padding:22px 24px}")
                .append(".brand{font-size:11px;letter-spacing:.14em;text-transform:uppercase;opacity:.85;margin:0 0 6px}")
                .append("h1{margin:0;font-size:22px;font-weight:700}.meta{margin:8px 0 0;opacity:.9;font-size:12px}")
                .append(".body{padding:20px 24px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px 16px;margin:0 0 18px}")
                .append(".item span{display:block;font-size:10px;color:#64748b;text-transform:uppercase;letter-spacing:.06em}")
                .append(".item strong{font-size:14px}h2{font-size:14px;margin:0 0 10px;color:#1d4ed8}")
                .append("table{border-collapse:collapse;width:100%;font-size:12px}")
                .append("th{background:#f1f7fc;text-align:left;padding:8px 9px;border-bottom:1px solid #d7e3ef}")
                .append("td{padding:7px 9px;border-bottom:1px solid #e8eef5}")
                .append("td.num,th.num{text-align:right}.sig{margin-top:28px;padding-top:14px;border-top:1px dashed #c9d9ea;")
                .append("font-style:italic;color:#2563eb}.foot{padding:12px 24px;background:#f7fbff;font-size:11px;color:#64748b}")
                .append("@media print{body{background:#fff}}")
                .append("</style></head><body><div class='doc'><div class='hero'>")
                .append("<p class='brand'>FinanTech S.A.</p><h1>Contrato de préstamo</h1>")
                .append("<p class='meta'>").append(loan.getCodigo())
                .append(" · Firma digital simulada · ").append(firma).append("</p></div><div class='body'>");
        sb.append("<div class='grid'>");
        sb.append("<div class='item'><span>Cliente</span><strong>")
                .append(esc(loan.getClienteNombre())).append("</strong></div>");
        sb.append("<div class='item'><span>DNI</span><strong>").append(loan.getDni()).append("</strong></div>");
        if (request != null && request.getDireccion() != null && !request.getDireccion().isBlank()) {
            sb.append("<div class='item'><span>Dirección</span><strong>")
                    .append(esc(request.getDireccion())).append("</strong></div>");
        }
        sb.append("<div class='item'><span>Monto desembolsado</span><strong>S/ ")
                .append(money(loan.getMonto())).append("</strong></div>");
        sb.append("<div class='item'><span>Cuotas</span><strong>").append(loan.getCuotas())
                .append(" (").append(loan.getFrecuencia().name().toLowerCase(Locale.ROOT)).append(")</strong></div>");
        sb.append("<div class='item'><span>Cuota</span><strong>S/ ")
                .append(money(loan.getCuotaMonto())).append("</strong></div>");
        sb.append("<div class='item'><span>Interés total</span><strong>S/ ")
                .append(money(loan.getInteresTotal())).append("</strong></div>");
        sb.append("<div class='item'><span>Total a pagar</span><strong>S/ ")
                .append(money(loan.getTotalPagar())).append("</strong></div>");
        sb.append("</div>");
        sb.append("<h2>Cronograma de amortización</h2><table><thead><tr>")
                .append("<th>#</th><th>Vence</th><th class='num'>Capital</th><th class='num'>Interés</th>")
                .append("<th class='num'>Cuota</th></tr></thead><tbody>");
        for (LoanInstallment i : rows) {
            sb.append("<tr><td>").append(i.getNumero()).append("</td><td>")
                    .append(i.getFechaVence().format(DATE_FMT)).append("</td><td class='num'>S/ ")
                    .append(money(i.getCapital())).append("</td><td class='num'>S/ ")
                    .append(money(i.getInteres())).append("</td><td class='num'>S/ ")
                    .append(money(i.getMonto())).append("</td></tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<p class='sig'>").append(esc(loan.getClienteNombre()))
                .append("<br/>Firma digital simulada · ").append(firma).append("</p></div>");
        sb.append("<div class='foot'>Documento generado por FinanTech S.A. · Uso interno / simulación de crédito</div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String buildVoucherHtml(ActiveLoan loan, LoanPayment payment, List<String> nums) {
        return "<!DOCTYPE html><html lang='es'><head><meta charset='utf-8'><title>Comprobante "
                + esc(payment.getCodigoVoucher())
                + "</title><style>"
                + "*{box-sizing:border-box}html,body{margin:0;padding:0}"
                + "body{font-family:'Segoe UI',Arial,sans-serif;color:#0a2540;background:#fff}"
                + ".doc{width:100%;background:#fff}"
                + ".hero{background:linear-gradient(135deg,#0a2540,#0ea5e9);color:#fff;padding:18px 22px}"
                + ".brand{margin:0 0 6px;font-size:11px;letter-spacing:.14em;text-transform:uppercase;opacity:.85}"
                + "h1{margin:0;font-size:20px}.body{padding:18px 22px}.row{display:flex;justify-content:space-between;"
                + "gap:12px;padding:9px 0;border-bottom:1px solid #e8eef5;font-size:13px}"
                + ".row span{color:#64748b}.row strong{text-align:right}"
                + ".total{margin-top:14px;padding:12px 14px;background:#f1f7fc;"
                + "display:flex;justify-content:space-between;align-items:center}"
                + ".total strong{font-size:18px;color:#1d4ed8}"
                + ".foot{padding:10px 22px;background:#f7fbff;font-size:11px;color:#64748b}"
                + "@media print{body{background:#fff}}"
                + "</style></head><body><div class='doc'><div class='hero'>"
                + "<p class='brand'>FinanTech S.A.</p><h1>Comprobante de pago</h1></div><div class='body'>"
                + "<div class='row'><span>Voucher</span><strong>" + esc(payment.getCodigoVoucher()) + "</strong></div>"
                + "<div class='row'><span>Préstamo</span><strong>" + loan.getCodigo() + "</strong></div>"
                + "<div class='row'><span>Cliente</span><strong>" + esc(loan.getClienteNombre())
                + " · DNI " + loan.getDni() + "</strong></div>"
                + "<div class='row'><span>Medio</span><strong>" + esc(payment.getMedio()) + "</strong></div>"
                + "<div class='row'><span>Cuotas</span><strong>" + String.join(", ", nums) + "</strong></div>"
                + "<div class='row'><span>Detalle</span><strong>" + esc(blank(payment.getDetalleMedio())) + "</strong></div>"
                + "<div class='total'><span>Monto pagado</span><strong>S/ " + money(payment.getMonto()) + "</strong></div>"
                + "</div><div class='foot'>Simulación de pago · FinanTech S.A.</div></div></body></html>";
    }

    private record LoanRequestResponseWrapper(LoanRequest request) {
        com.operaciones.backend.credit.dto.LoanRequestResponse response() {
            return new com.operaciones.backend.credit.dto.LoanRequestResponse(
                    request.getId(),
                    request.getClienteNombre(),
                    request.getNombres(),
                    request.getApellidos(),
                    request.getDni(),
                    request.getDireccion(),
                    request.getTrabajo(),
                    money(request.getIngresosMensuales()),
                    money(request.getMonto()),
                    request.getCuotas(),
                    request.getFrecuenciaPago() == null ? "MENSUAL" : request.getFrecuenciaPago().name(),
                    request.getEstado().name(),
                    request.getPeriodoTasa(),
                    request.getValorTasa() == null ? null : plain(request.getValorTasa()),
                    request.getTasaMensualUsada() == null
                            ? null
                            : plain(request.getTasaMensualUsada().multiply(HUNDRED).setScale(6, RoundingMode.HALF_UP)),
                    money(request.getCuotaMensual()),
                    money(request.getInteresTotal()),
                    money(request.getTotalPagar()),
                    request.getCreadoEn() == null ? null : request.getCreadoEn().toString());
        }
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /** Compacta "1,2,3,4,8,9" → "1-4,8-9" para no saturar la columna. */
    private static String formatCuotasLabel(List<String> nums) {
        if (nums == null || nums.isEmpty()) {
            return "";
        }
        List<Integer> sorted = nums.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .sorted()
                .distinct()
                .toList();
        if (sorted.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int start = sorted.get(0);
        int prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            int n = sorted.get(i);
            if (n == prev + 1) {
                prev = n;
                continue;
            }
            appendRange(out, start, prev);
            start = prev = n;
        }
        appendRange(out, start, prev);
        return out.toString();
    }

    private static void appendRange(StringBuilder out, int start, int end) {
        if (!out.isEmpty()) {
            out.append(',');
        }
        if (start == end) {
            out.append(start);
        } else {
            out.append(start).append('-').append(end);
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String esc(String value) {
        return blank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String extractBearer(String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        return value;
    }
}
