package com.operaciones.backend.credit;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.operaciones.backend.credit.dto.ActiveLoanResponse;
import com.operaciones.backend.credit.dto.CalcRequest;
import com.operaciones.backend.credit.dto.CalcResponse;
import com.operaciones.backend.credit.dto.CashSummaryResponse;
import com.operaciones.backend.credit.dto.ClientBlockRequest;
import com.operaciones.backend.credit.dto.ClientDossierResponse;
import com.operaciones.backend.credit.dto.ClientPortalResponse;
import com.operaciones.backend.credit.dto.ContractResponse;
import com.operaciones.backend.credit.dto.CreditParamRequest;
import com.operaciones.backend.credit.dto.CreditParamResponse;
import com.operaciones.backend.credit.dto.DueInstallmentResponse;
import com.operaciones.backend.credit.dto.InstallmentResponse;
import com.operaciones.backend.credit.dto.LoanRequestCreate;
import com.operaciones.backend.credit.dto.LoanRequestResponse;
import com.operaciones.backend.credit.dto.LoanRequestStatusPatch;
import com.operaciones.backend.credit.dto.NotificationResponse;
import com.operaciones.backend.credit.dto.PaymentCreateRequest;
import com.operaciones.backend.credit.dto.PaymentResponse;
import com.operaciones.backend.credit.dto.ReminderRequest;
import com.operaciones.backend.credit.dto.ScheduleShiftRequest;
import com.operaciones.backend.auth.AuthService;
import com.operaciones.backend.mail.MailService;

@RestController
@RequestMapping("/api/credit")
public class CreditController {

    private final CreditService creditService;
    private final LoanBookService loanBookService;
    private final CashService cashService;
    private final MailService mailService;
    private final AuthService authService;

    public CreditController(
            CreditService creditService,
            LoanBookService loanBookService,
            CashService cashService,
            MailService mailService,
            AuthService authService) {
        this.creditService = creditService;
        this.loanBookService = loanBookService;
        this.cashService = cashService;
        this.mailService = mailService;
        this.authService = authService;
    }

    @GetMapping("/params")
    public List<CreditParamResponse> listParams(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return creditService.listParams(authorization);
    }

    @PostMapping("/params")
    public CreditParamResponse createParam(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreditParamRequest request) {
        return creditService.createParam(authorization, request);
    }

    @PutMapping("/params/{id}")
    public CreditParamResponse updateParam(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody CreditParamRequest request) {
        return creditService.updateParam(authorization, id, request);
    }

    @PatchMapping("/params/{id}/vigente")
    public CreditParamResponse setVigente(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean vigente = body == null ? null : body.get("vigente");
        if (vigente == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Indica si está vigente");
        }
        return creditService.setVigente(authorization, id, vigente);
    }

    @GetMapping("/plazos")
    public List<Integer> plazos(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return creditService.listAllowedTerms(authorization);
    }

    @PostMapping("/calculate")
    public CalcResponse calculate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CalcRequest request) {
        return creditService.calculate(authorization, request);
    }

    @GetMapping("/requests")
    public List<LoanRequestResponse> listRequests(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return creditService.listRequests(authorization);
    }

    @PostMapping("/requests")
    public LoanRequestResponse createRequest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LoanRequestCreate request) {
        authService.requireClientEmailVerified(authorization);
        return creditService.createRequest(authorization, request);
    }

    @PatchMapping("/requests/{id}/estado")
    public LoanRequestResponse patchStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody LoanRequestStatusPatch request) {
        return creditService.patchStatus(authorization, id, request);
    }

    @GetMapping("/loans")
    public List<ActiveLoanResponse> listLoans(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "estado", required = false) String estado) {
        return loanBookService.listLoans(authorization, estado);
    }

    @GetMapping("/loans/{id}")
    public ActiveLoanResponse getLoan(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        return loanBookService.getLoan(authorization, id);
    }

    @PatchMapping("/loans/{id}/schedule")
    public ActiveLoanResponse shiftSchedule(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody ScheduleShiftRequest request) {
        return loanBookService.shiftSchedule(authorization, id, request.fechaInicio());
    }

    @PostMapping("/loans/{id}/pay")
    public PaymentResponse staffPay(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody PaymentCreateRequest request) {
        return loanBookService.staffPay(authorization, id, request);
    }

    @GetMapping("/installments/due")
    public List<DueInstallmentResponse> dueInstallments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.dueInstallments(authorization);
    }

    @PostMapping("/reminders")
    public Map<String, Object> reminder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ReminderRequest request) {
        return loanBookService.sendReminder(authorization, request, mailService);
    }

    @GetMapping("/cash")
    public CashSummaryResponse cash(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return cashService.summary(authorization);
    }

    @GetMapping("/clients/{dni}/dossier")
    public ClientDossierResponse dossier(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String dni) {
        return loanBookService.dossier(authorization, dni);
    }

    @PostMapping("/clients/block")
    public Map<String, Object> blockClient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ClientBlockRequest request) {
        return loanBookService.blockClient(authorization, request);
    }

    @DeleteMapping("/clients/{dni}/block")
    public Map<String, Object> unblockClient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String dni) {
        return loanBookService.unblockClient(authorization, dni);
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> staffNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.staffNotifications(authorization);
    }

    @GetMapping("/me/portal")
    public ClientPortalResponse portal(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.portal(authorization);
    }

    @GetMapping("/me/loan")
    public ActiveLoanResponse myLoan(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.myLoan(authorization);
    }

    @GetMapping("/me/schedule")
    public List<InstallmentResponse> mySchedule(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.mySchedule(authorization);
    }

    @PostMapping("/me/pay")
    public PaymentResponse pay(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PaymentCreateRequest request) {
        authService.requireClientEmailVerified(authorization);
        return loanBookService.pay(authorization, request);
    }

    @GetMapping("/me/payments")
    public List<PaymentResponse> myPayments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.myPayments(authorization);
    }

    @GetMapping("/me/contract")
    public ContractResponse myContract(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.myContract(authorization);
    }

    @GetMapping("/me/vouchers")
    public List<PaymentResponse> myVouchers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.myVouchers(authorization);
    }

    @GetMapping("/me/notifications")
    public List<NotificationResponse> myNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return loanBookService.myNotifications(authorization);
    }

    @GetMapping("/me/notifications/unread")
    public Map<String, Long> unread(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Map.of("count", loanBookService.unreadCount(authorization));
    }

    @PostMapping("/me/notifications/read")
    public Map<String, String> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        loanBookService.markNotificationsRead(authorization);
        return Map.of("status", "ok");
    }
}
