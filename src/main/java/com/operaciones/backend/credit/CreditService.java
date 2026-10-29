package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.operaciones.backend.auth.AuthTokenStore;
import com.operaciones.backend.auth.AuthTokenStore.Session;
import com.operaciones.backend.auth.UsuarioDni;
import com.operaciones.backend.auth.UsuarioDniRepository;
import com.operaciones.backend.credit.dto.CalcRequest;
import com.operaciones.backend.credit.dto.CalcResponse;
import com.operaciones.backend.credit.dto.CreditParamRequest;
import com.operaciones.backend.credit.dto.CreditParamResponse;
import com.operaciones.backend.credit.dto.LoanRequestCreate;
import com.operaciones.backend.credit.dto.LoanRequestResponse;
import com.operaciones.backend.credit.dto.LoanRequestStatusPatch;

@Service
public class CreditService {

    private static final MathContext MATH = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal WEEKS_PER_MONTH = BigDecimal.valueOf(4);

    private final CreditParamRepository paramRepository;
    private final LoanRequestRepository loanRepository;
    private final ActiveLoanRepository activeLoanRepository;
    private final AuthTokenStore tokenStore;
    private final UsuarioDniRepository dniRepository;
    private final LoanBookService loanBookService;

    public CreditService(
            CreditParamRepository paramRepository,
            LoanRequestRepository loanRepository,
            ActiveLoanRepository activeLoanRepository,
            AuthTokenStore tokenStore,
            UsuarioDniRepository dniRepository,
            @Lazy LoanBookService loanBookService) {
        this.paramRepository = paramRepository;
        this.loanRepository = loanRepository;
        this.activeLoanRepository = activeLoanRepository;
        this.tokenStore = tokenStore;
        this.dniRepository = dniRepository;
        this.loanBookService = loanBookService;
    }

    public List<CreditParamResponse> listParams(String bearer) {
        requireStaff(bearer);
        return paramRepository.findAllByOrderByIdAsc().stream().map(this::toParam).toList();
    }

    public CreditParamResponse createParam(String bearer, CreditParamRequest request) {
        requireStaff(bearer);
        String codigo = requireText(request.codigo(), "El código es obligatorio").toUpperCase(Locale.ROOT);
        if (paramRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un parámetro con ese código");
        }
        CreditParam param = new CreditParam();
        applyParam(param, request, codigo);
        return toParam(paramRepository.save(param));
    }

    public CreditParamResponse updateParam(String bearer, Long id, CreditParamRequest request) {
        requireStaff(bearer);
        CreditParam param = paramRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parámetro no encontrado"));
        String codigo = requireText(request.codigo(), "El código es obligatorio").toUpperCase(Locale.ROOT);
        paramRepository.findByCodigoIgnoreCase(codigo).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un parámetro con ese código");
            }
        });
        applyParam(param, request, codigo);
        return toParam(paramRepository.save(param));
    }

    public CreditParamResponse setVigente(String bearer, Long id, boolean vigente) {
        requireStaff(bearer);
        CreditParam param = paramRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parámetro no encontrado"));
        param.setVigente(vigente);
        return toParam(paramRepository.save(param));
    }

    public CalcResponse calculate(String bearer, CalcRequest request) {
        requireAny(bearer);
        BigDecimal monto = parseMoney(request.monto());
        int cuotas = requireCuotas(request.cuotas());
        PaymentFrequency freq = parseFrequency(request.frecuenciaPago());
        List<Integer> plazos = allowedTerms();
        if (freq == PaymentFrequency.MENSUAL && !plazos.contains(cuotas)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Plazo no permitido. Usa: " + plazos.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        if (freq == PaymentFrequency.SEMANAL && (cuotas < 4 || cuotas > 104)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para pago semanal usa entre 4 y 104 cuotas");
        }
        CreditParam tasa = resolveInterest(request.tasaParamId());
        CreditParam mora = activeMora().orElse(null);
        Quote quote = quote(monto, cuotas, tasa, freq);
        return new CalcResponse(
                money(monto),
                cuotas,
                tasa.getPeriodo().name(),
                plain(tasa.getValor()),
                plain(quote.monthlyRate().multiply(HUNDRED).setScale(6, RoundingMode.HALF_UP)),
                money(quote.installment()),
                money(quote.totalInterest()),
                money(quote.totalPay()),
                mora == null ? "0" : plain(mora.getValor()),
                plazos);
    }

    public List<Integer> listAllowedTerms(String bearer) {
        requireAny(bearer);
        return allowedTerms();
    }

    public List<LoanRequestResponse> listRequests(String bearer) {
        requireStaff(bearer);
        return loanRepository.findAllByOrderByCreadoEnDesc().stream().map(this::toLoan).toList();
    }

    public LoanRequestResponse createRequest(String bearer, LoanRequestCreate request) {
        Session session = requireAny(bearer);
        boolean staff = "empresa".equals(session.kind());
        String nombres = blank(request.nombres());
        String apellidos = blank(request.apellidos());
        String nombre = blank(request.clienteNombre());
        if (nombre.isBlank()) {
            nombre = (nombres + " " + apellidos).trim();
        }
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica nombres y apellidos");
        }
        String dni;
        if (staff) {
            dni = requireDni(request.dni());
        } else {
            UsuarioDni user = dniRepository.findById(session.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
            dni = user.getDni();
        }
        assertCanCreateForDni(dni);
        BigDecimal monto = parseMoney(request.monto());
        int cuotas = requireCuotas(request.cuotas());
        PaymentFrequency freq = parseFrequency(request.frecuenciaPago());
        List<Integer> plazos = allowedTerms();
        if (freq == PaymentFrequency.MENSUAL && !plazos.contains(cuotas)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plazo no permitido");
        }
        if (freq == PaymentFrequency.SEMANAL && (cuotas < 4 || cuotas > 104)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para pago semanal usa entre 4 y 104 cuotas");
        }
        CreditParam tasa = resolveInterest(request.tasaParamId());
        Quote quote = quote(monto, cuotas, tasa, freq);

        LoanRequest loan = new LoanRequest();
        loan.setClienteNombre(nombre);
        loan.setNombres(nombres.isBlank() ? nombre : nombres);
        loan.setApellidos(apellidos);
        loan.setDni(dni);
        loan.setDireccion(blank(request.direccion()));
        loan.setTrabajo(blank(request.trabajo()));
        if (request.ingresosMensuales() != null && !request.ingresosMensuales().isBlank()) {
            loan.setIngresosMensuales(parseMoney(request.ingresosMensuales()));
        }
        loan.setFrecuenciaPago(freq);
        loan.setMonto(monto);
        loan.setCuotas(cuotas);
        loan.setEstado(LoanRequestStatus.EN_EVALUACION);
        loan.setPeriodoTasa(tasa.getPeriodo().name());
        loan.setValorTasa(tasa.getValor());
        loan.setTasaMensualUsada(quote.monthlyRate());
        loan.setCuotaMensual(quote.installment());
        loan.setInteresTotal(quote.totalInterest());
        loan.setTotalPagar(quote.totalPay());
        return toLoan(loanRepository.save(loan));
    }

    @Transactional
    public LoanRequestResponse patchStatus(String bearer, Long id, LoanRequestStatusPatch request) {
        requireStaff(bearer);
        LoanRequest loan = loanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        String raw = requireText(request.estado(), "Indica el estado").toUpperCase(Locale.ROOT);
        LoanRequestStatus next;
        try {
            next = LoanRequestStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido");
        }
        if (loan.getEstado() == LoanRequestStatus.APROBADA && next != LoanRequestStatus.APROBADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue aprobada");
        }
        loan.setEstado(next);
        loan = loanRepository.save(loan);
        if (next == LoanRequestStatus.APROBADA) {
            PaymentFrequency freq = loan.getFrecuenciaPago() == null ? PaymentFrequency.MENSUAL : loan.getFrecuenciaPago();
            Quote quote;
            if (loan.getTasaMensualUsada() != null
                    && loan.getCuotaMensual() != null
                    && loan.getInteresTotal() != null
                    && loan.getTotalPagar() != null) {
                quote = new Quote(
                        loan.getTasaMensualUsada(),
                        loan.getCuotaMensual(),
                        loan.getInteresTotal(),
                        loan.getTotalPagar());
            } else {
                CreditParam tasa = activeInterest();
                quote = quote(loan.getMonto(), loan.getCuotas(), tasa, freq);
            }
            loanBookService.activateFromRequest(loan, quote);
        } else if (next == LoanRequestStatus.RECHAZADA) {
            loanBookService.notifyRejection(loan);
        }
        return toLoan(loan);
    }

    private void assertCanCreateForDni(String dni) {
        if (loanBookService.isBlocked(dni)) {
            var block = loanBookService.activeBlock(dni).orElse(null);
            String hasta = block == null ? "" : " hasta el " + block.getHasta();
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Este DNI tiene préstamos temporalmente deshabilitados" + hasta);
        }
        if (activeLoanRepository.existsByDniAndEstado(dni, "VIGENTE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un préstamo vigente para este DNI");
        }
        if (loanRepository.existsByDniAndEstadoIn(
                dni, List.of(LoanRequestStatus.EN_EVALUACION, LoanRequestStatus.OBSERVADA))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una solicitud pendiente para este DNI");
        }
    }

    /**
     * Convierte la tasa configurada a tasa por periodo (mensual o semanal)
     * y calcula cuota francesa: P * r * (1+r)^n / ((1+r)^n - 1).
     */
    Quote quote(BigDecimal principal, int n, CreditParam tasa, PaymentFrequency freq) {
        BigDecimal monthly = toMonthlyRate(tasa.getPeriodo(), tasa.getValor());
        BigDecimal periodRate = freq == PaymentFrequency.SEMANAL
                ? monthly.divide(WEEKS_PER_MONTH, MATH)
                : monthly;
        BigDecimal installment;
        if (periodRate.compareTo(BigDecimal.ZERO) == 0) {
            installment = principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal onePlus = BigDecimal.ONE.add(periodRate, MATH);
            BigDecimal pow = onePlus.pow(n, MATH);
            BigDecimal numerator = principal.multiply(periodRate, MATH).multiply(pow, MATH);
            BigDecimal denominator = pow.subtract(BigDecimal.ONE, MATH);
            installment = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        }
        BigDecimal totalPay = installment.multiply(BigDecimal.valueOf(n)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = totalPay.subtract(principal).setScale(2, RoundingMode.HALF_UP);
        return new Quote(periodRate, installment, interest, totalPay);
    }

    static BigDecimal toMonthlyRate(RatePeriod periodo, BigDecimal percent) {
        BigDecimal decimal = percent.divide(HUNDRED, MATH);
        return switch (periodo) {
            case MENSUAL -> decimal;
            case ANUAL -> decimal.divide(MONTHS_PER_YEAR, MATH);
            case DIARIA -> decimal.multiply(DAYS_PER_MONTH, MATH);
            case FIJO -> BigDecimal.ZERO;
        };
    }

    private void applyParam(CreditParam param, CreditParamRequest request, String codigo) {
        ParamKind kind;
        RatePeriod periodo;
        try {
            kind = ParamKind.valueOf(requireText(request.kind(), "Indica el tipo").toUpperCase(Locale.ROOT));
            periodo = RatePeriod.valueOf(requireText(request.periodo(), "Indica el periodo").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo o periodo no válido");
        }
        if (kind == ParamKind.PLAZO) {
            periodo = RatePeriod.FIJO;
        }
        BigDecimal valor = parseDecimal(request.valor(), "El valor es obligatorio");
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor no puede ser negativo");
        }
        String plazos = normalizeTerms(request.plazosPermitidos());
        param.setCodigo(codigo);
        param.setNombre(requireText(request.nombre(), "El nombre es obligatorio"));
        param.setDescripcion(blank(request.descripcion()));
        param.setKind(kind);
        param.setPeriodo(periodo);
        param.setValor(valor);
        param.setPlazosPermitidos(plazos);
        param.setVigente(request.vigente() == null || request.vigente());
    }

    private CreditParam activeInterest() {
        return paramRepository.findByKindAndVigenteTrueOrderByIdAsc(ParamKind.TASA_INTERES).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No hay tasa de interés vigente. Configúrala en Parámetros de crédito."));
    }

    private CreditParam resolveInterest(Long tasaParamId) {
        if (tasaParamId == null) {
            return activeInterest();
        }
        CreditParam param = paramRepository.findById(tasaParamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tasa de interés no encontrada"));
        if (param.getKind() != ParamKind.TASA_INTERES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro seleccionado no es una tasa de interés");
        }
        if (!param.isVigente()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tasa seleccionada no está vigente");
        }
        return param;
    }

    private java.util.Optional<CreditParam> activeMora() {
        return paramRepository.findByKindAndVigenteTrueOrderByIdAsc(ParamKind.MORA).stream().findFirst();
    }

    private List<Integer> allowedTerms() {
        List<CreditParam> plazos = paramRepository.findByKindAndVigenteTrueOrderByIdAsc(ParamKind.PLAZO);
        List<Integer> fromPlazo = new ArrayList<>();
        for (CreditParam p : plazos) {
            fromPlazo.addAll(parseTerms(p.getPlazosPermitidos()));
            int max = p.getValor().intValue();
            if (max > 0 && !fromPlazo.contains(max)) {
                // valor = máximo; plazosPermitidos manda la lista
            }
        }
        if (!fromPlazo.isEmpty()) {
            return fromPlazo.stream().distinct().sorted().toList();
        }
        CreditParam tasa = activeInterest();
        List<Integer> fromTasa = parseTerms(tasa.getPlazosPermitidos());
        if (!fromTasa.isEmpty()) {
            return fromTasa;
        }
        return List.of(6, 8, 12, 18, 24);
    }

    private CreditParamResponse toParam(CreditParam p) {
        List<Integer> plazos = parseTerms(p.getPlazosPermitidos());
        return new CreditParamResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombre(),
                p.getDescripcion(),
                p.getKind().name(),
                p.getPeriodo().name(),
                plain(p.getValor()),
                p.getPlazosPermitidos(),
                plazos,
                p.isVigente());
    }

    private LoanRequestResponse toLoan(LoanRequest loan) {
        return new LoanRequestResponse(
                loan.getId(),
                loan.getClienteNombre(),
                loan.getNombres(),
                loan.getApellidos(),
                loan.getDni(),
                loan.getDireccion(),
                loan.getTrabajo(),
                money(loan.getIngresosMensuales()),
                money(loan.getMonto()),
                loan.getCuotas(),
                loan.getFrecuenciaPago() == null ? "MENSUAL" : loan.getFrecuenciaPago().name(),
                loan.getEstado().name(),
                loan.getPeriodoTasa(),
                loan.getValorTasa() == null ? null : plain(loan.getValorTasa()),
                loan.getTasaMensualUsada() == null
                        ? null
                        : plain(loan.getTasaMensualUsada().multiply(HUNDRED).setScale(6, RoundingMode.HALF_UP)),
                money(loan.getCuotaMensual()),
                money(loan.getInteresTotal()),
                money(loan.getTotalPagar()),
                loan.getCreadoEn() == null ? null : loan.getCreadoEn().toString());
    }

    private Session requireStaff(String bearerToken) {
        Session session = requireAny(bearerToken);
        if (!"empresa".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo personal de FinanTech S.A.");
        }
        return session;
    }

    private Session requireAny(String bearerToken) {
        return tokenStore.findSession(extractBearer(bearerToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }

    private static PaymentFrequency parseFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            return PaymentFrequency.MENSUAL;
        }
        try {
            return PaymentFrequency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Frecuencia no válida (MENSUAL o SEMANAL)");
        }
    }

    private static List<Integer> parseTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .filter(n -> n > 0 && n <= 120)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String normalizeTerms(String raw) {
        List<Integer> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return "6,8,12,18,24";
        }
        return terms.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static BigDecimal parseMoney(String raw) {
        BigDecimal value = parseDecimal(raw, "El monto es obligatorio");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseDecimal(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ".")).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número no válido");
        }
    }

    private static int requireCuotas(Integer cuotas) {
        if (cuotas == null || cuotas < 1 || cuotas > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un número de cuotas válido");
        }
        return cuotas;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static String requireDni(String value) {
        String dni = requireText(value, "El DNI es obligatorio");
        if (!dni.matches("\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener 8 dígitos");
        }
        return dni;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
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

    record Quote(BigDecimal monthlyRate, BigDecimal installment, BigDecimal totalInterest, BigDecimal totalPay) {
    }
}
