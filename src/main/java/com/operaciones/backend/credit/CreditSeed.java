package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.operaciones.backend.auth.UsuarioDni;
import com.operaciones.backend.auth.UsuarioDniRepository;

@Component
public class CreditSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CreditSeed.class);
    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal RATE = new BigDecimal("0.045");

    private final CreditParamRepository paramRepository;
    private final UsuarioDniRepository dniRepository;
    private final ActiveLoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanPaymentRepository paymentRepository;
    private final LoanContractRepository contractRepository;
    private final LoanRequestRepository requestRepository;
    private final CreditNotificationRepository notificationRepository;
    private final CashService cashService;
    private final PasswordEncoder passwordEncoder;

    public CreditSeed(
            CreditParamRepository paramRepository,
            UsuarioDniRepository dniRepository,
            ActiveLoanRepository loanRepository,
            LoanInstallmentRepository installmentRepository,
            LoanPaymentRepository paymentRepository,
            LoanContractRepository contractRepository,
            LoanRequestRepository requestRepository,
            CreditNotificationRepository notificationRepository,
            CashService cashService,
            PasswordEncoder passwordEncoder) {
        this.paramRepository = paramRepository;
        this.dniRepository = dniRepository;
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.requestRepository = requestRepository;
        this.notificationRepository = notificationRepository;
        this.cashService = cashService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedParams();
        cashService.ensureSeed();
        seedClientsAndPortfolio();
    }

    private void seedParams() {
        if (paramRepository.count() > 0) {
            return;
        }
        paramRepository.save(param(
                "TASA_MENSUAL",
                "Tasa de interés",
                "Interés aplicado al cronograma de cuotas",
                ParamKind.TASA_INTERES,
                RatePeriod.MENSUAL,
                "4.5",
                "6,8,12,18,24"));
        paramRepository.save(param(
                "MORA_DIARIA",
                "Mora diaria",
                "Recargo diario sobre cuota vencida",
                ParamKind.MORA,
                RatePeriod.DIARIA,
                "0.15",
                "6,8,12,18,24"));
        paramRepository.save(param(
                "PLAZO_MAX",
                "Plazos permitidos",
                "Número máximo y lista de cuotas por crédito",
                ParamKind.PLAZO,
                RatePeriod.FIJO,
                "24",
                "6,8,12,18,24"));
        log.info("Parámetros de crédito iniciales creados");
    }

    private void seedClientsAndPortfolio() {
        if (loanRepository.count() > 0) {
            ensureClient("45678912", "Ana Quispe", "Ana Cliente", "999111222",
                    "Av. Arequipa 1200, Lima", "Comerciante", "2800");
            ensureClient(
                    "11223344", "Pedro Sincredito", "Pedro Cliente", "966554433",
                    "Av. Brasil 500, Lima", "Chofer", "1600");
            return;
        }

        UsuarioDni ana = ensureClient(
                "45678912", "Ana Quispe", "Ana Cliente", "999111222",
                "Av. Arequipa 1200, Lima", "Comerciante", "2800");
        UsuarioDni luis = ensureClient(
                "87654321", "Luis Demo", "Luis Demo", "988776655",
                "Jr. Cusco 450, Lima", "Empleado", "2200");
        UsuarioDni rosa = ensureClient(
                "33445566", "Rosa Mendoza", "Rosa Mendoza", "977665544",
                "Calle Los Olivos 88, Callao", "Independiente", "1900");
        // Cliente sin préstamo para probar el flujo "Solicitar préstamo"
        ensureClient(
                "11223344", "Pedro Sincredito", "Pedro Cliente", "966554433",
                "Av. Brasil 500, Lima", "Chofer", "1600");

        ActiveLoan pt1042 = seedLoan(
                "PT-1042", ana, new BigDecimal("3500.00"), 12,
                LocalDate.now().minusMonths(4).withDayOfMonth(5), 4, false);
        ActiveLoan pt1108 = seedLoan(
                "PT-1108", luis, new BigDecimal("1200.00"), 8,
                LocalDate.now().minusMonths(2).withDayOfMonth(12), 2, false);
        seedLoan(
                "PT-0981", rosa, new BigDecimal("2800.00"), 10,
                LocalDate.now().minusMonths(8).withDayOfMonth(18), 6, true);

        CreditNotification n = new CreditNotification();
        n.setDni(ana.getDni());
        n.setTitulo("Bienvenida FinanTech");
        n.setMensaje("Tu préstamo PT-1042 está activo. Revisa tu cronograma y paga a tiempo.");
        notificationRepository.save(n);

        log.info("Portafolio demo sembrado: Ana / Luis / Rosa · caja {}", cashService.currentBalance());
    }

    private UsuarioDni ensureClient(
            String dni,
            String nombres,
            String apodo,
            String telefono,
            String direccion,
            String trabajo,
            String ingresos) {
        return dniRepository.findByDni(dni).map(existing -> {
            if (existing.getDireccion() == null) {
                existing.setDireccion(direccion);
                existing.setTrabajo(trabajo);
                existing.setIngresosMensuales(new BigDecimal(ingresos));
                existing.setTelefono(telefono);
                return dniRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            UsuarioDni cliente = new UsuarioDni();
            cliente.setNombres(nombres);
            cliente.setApodo(apodo);
            cliente.setDni(dni);
            cliente.setPasswordHash(passwordEncoder.encode("Cliente123!"));
            cliente.setMustChangePassword(false);
            cliente.setActivo(true);
            cliente.setTelefono(telefono);
            cliente.setDireccion(direccion);
            cliente.setTrabajo(trabajo);
            cliente.setIngresosMensuales(new BigDecimal(ingresos));
            UsuarioDni saved = dniRepository.save(cliente);
            log.info("Cliente demo: DNI {} / Cliente123!", dni);
            return saved;
        });
    }

    private ActiveLoan seedLoan(
            String codigo,
            UsuarioDni user,
            BigDecimal monto,
            int cuotas,
            LocalDate start,
            int paidCount,
            boolean withOverdue) {
        BigDecimal installment = frenchInstallment(monto, cuotas, RATE);
        BigDecimal totalPay = installment.multiply(BigDecimal.valueOf(cuotas)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = totalPay.subtract(monto).setScale(2, RoundingMode.HALF_UP);

        LoanRequest req = new LoanRequest();
        req.setClienteNombre(user.getNombres());
        req.setNombres(user.getNombres());
        req.setApellidos("");
        req.setDni(user.getDni());
        req.setDireccion(user.getDireccion());
        req.setTrabajo(user.getTrabajo());
        req.setIngresosMensuales(user.getIngresosMensuales());
        req.setFrecuenciaPago(PaymentFrequency.MENSUAL);
        req.setMonto(monto);
        req.setCuotas(cuotas);
        req.setEstado(LoanRequestStatus.APROBADA);
        req.setPeriodoTasa("MENSUAL");
        req.setValorTasa(new BigDecimal("4.5"));
        req.setTasaMensualUsada(RATE);
        req.setCuotaMensual(installment);
        req.setInteresTotal(interest);
        req.setTotalPagar(totalPay);
        req = requestRepository.save(req);

        ActiveLoan loan = new ActiveLoan();
        loan.setCodigo(codigo);
        loan.setRequestId(req.getId());
        loan.setDni(user.getDni());
        loan.setClienteNombre(user.getNombres());
        loan.setMonto(monto);
        loan.setCuotas(cuotas);
        loan.setFrecuencia(PaymentFrequency.MENSUAL);
        loan.setTasaPeriodo(RATE);
        loan.setCuotaMonto(installment);
        loan.setInteresTotal(interest);
        loan.setTotalPagar(totalPay);
        loan.setFechaInicio(start);
        loan.setEstado("VIGENTE");
        loan = loanRepository.save(loan);

        BigDecimal balance = monto;
        List<LoanInstallment> rows = new ArrayList<>();
        for (int n = 1; n <= cuotas; n++) {
            LocalDate due = start.plusMonths(n);
            BigDecimal intAmt = balance.multiply(RATE, MATH).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capital = installment.subtract(intAmt).setScale(2, RoundingMode.HALF_UP);
            if (n == cuotas) {
                capital = balance;
                intAmt = installment.subtract(capital).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }
            if (capital.compareTo(balance) > 0) {
                capital = balance;
            }
            balance = balance.subtract(capital).max(BigDecimal.ZERO);

            LoanInstallment row = new LoanInstallment();
            row.setLoanId(loan.getId());
            row.setNumero(n);
            row.setFechaVence(due);
            row.setMonto(installment);
            row.setCapital(capital);
            row.setInteres(intAmt);
            if (n <= paidCount) {
                row.setEstado(InstallmentStatus.PAGADA);
                Instant paidAt = due.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(3600L * 8);
                if (withOverdue && n > paidCount - 2) {
                    paidAt = due.plusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant();
                }
                row.setPagadoEn(paidAt);
            } else if (due.isBefore(LocalDate.now())) {
                row.setEstado(InstallmentStatus.VENCIDA);
            } else {
                row.setEstado(InstallmentStatus.PENDIENTE);
            }
            rows.add(row);
        }
        installmentRepository.saveAll(rows);

        BigDecimal paidTotal = rows.stream()
                .filter(r -> r.getEstado() == InstallmentStatus.PAGADA)
                .map(LoanInstallment::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        loan.setSaldo(totalPay.subtract(paidTotal).max(BigDecimal.ZERO));
        loanRepository.save(loan);

        cashService.record(
                "DESEMBOLSO",
                monto.negate(),
                "Desembolso demo " + codigo + " · " + user.getNombres(),
                codigo,
                user.getDni());

        for (LoanInstallment row : rows) {
            if (row.getEstado() != InstallmentStatus.PAGADA) {
                continue;
            }
            LoanPayment payment = new LoanPayment();
            payment.setLoanId(loan.getId());
            payment.setDni(user.getDni());
            payment.setCodigoVoucher("VC-DEMO-" + codigo + "-" + row.getNumero());
            payment.setMonto(row.getMonto());
            payment.setMedio(row.getNumero() % 2 == 0 ? "Yape" : "Transferencia");
            payment.setDetalleMedio("Pago demo cuota " + row.getNumero());
            payment.setCuotasCubiertas(String.valueOf(row.getNumero()));
            payment.setVoucherHtml("<html><body>Voucher demo " + codigo + " cuota " + row.getNumero() + "</body></html>");
            paymentRepository.save(payment);
            cashService.record(
                    "COBRO",
                    row.getMonto(),
                    "Cobro demo " + codigo + " · cuota " + row.getNumero(),
                    payment.getCodigoVoucher(),
                    user.getDni());
        }

        LoanContract contract = new LoanContract();
        contract.setLoanId(loan.getId());
        contract.setDni(user.getDni());
        contract.setCodigo("CTR-" + codigo);
        contract.setFirmaSimulada("Firma digital simulada · " + user.getNombres());
        contract.setContenidoHtml(
                "<html><body><h1>Contrato " + codigo + "</h1><p>" + user.getNombres()
                        + " · DNI " + user.getDni() + " · Monto S/ " + monto + "</p></body></html>");
        contractRepository.save(contract);

        return loan;
    }

    private static BigDecimal frenchInstallment(BigDecimal principal, int n, BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        BigDecimal onePlus = BigDecimal.ONE.add(rate, MATH);
        BigDecimal pow = onePlus.pow(n, MATH);
        BigDecimal numerator = principal.multiply(rate, MATH).multiply(pow, MATH);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE, MATH);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static CreditParam param(
            String codigo,
            String nombre,
            String descripcion,
            ParamKind kind,
            RatePeriod periodo,
            String valor,
            String plazos) {
        CreditParam p = new CreditParam();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setKind(kind);
        p.setPeriodo(periodo);
        p.setValor(new BigDecimal(valor));
        p.setPlazosPermitidos(plazos);
        p.setVigente(true);
        return p;
    }
}
