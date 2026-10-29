package com.operaciones.backend.mail;

public final class MailTemplates {

    private MailTemplates() {
    }

    public static String verifyEmail(String nombre, String verifyUrl) {
        return shell(
                "Verifica tu correo",
                "Hola " + esc(nombre) + ",",
                """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.55;color:#334155">
                  Para usar FinanTech S.A. debes <strong>confirmar tu correo</strong>.
                  Sin verificación no podrás solicitar préstamos ni registrar pagos.
                </p>
                <p style="margin:0 0 22px;text-align:center">
                  <a href="%s" style="display:inline-block;background:#0066ff;color:#fff;text-decoration:none;
                  font-weight:700;font-size:14px;padding:12px 22px;border-radius:12px">Verificar correo</a>
                </p>
                <p style="margin:0;font-size:12px;line-height:1.5;color:#64748b">
                  Si el botón no funciona, copia este enlace:<br/>
                  <a href="%s" style="color:#2563eb;word-break:break-all">%s</a>
                </p>
                """.formatted(esc(verifyUrl), esc(verifyUrl), esc(verifyUrl)),
                "El enlace caduca en unos minutos. Si no pediste esto, ignora el mensaje.");
    }

    public static String welcomeClient(String nombre, String dni, String profileUrl) {
        return shell(
                "Bienvenido a FinanTech S.A.",
                "Hola " + esc(nombre) + ",",
                """
                <p style="margin:0 0 14px;font-size:15px;line-height:1.55;color:#334155">
                  Tu cuenta de cliente (DNI <strong>%s</strong>) ya está activa.
                </p>
                <p style="margin:0 0 16px;font-size:15px;line-height:1.55;color:#334155">
                  <strong>Paso obligatorio:</strong> registra y verifica tu correo en el perfil
                  para recibir avisos de cuotas y poder operar en la plataforma.
                </p>
                <p style="margin:0 0 8px;text-align:center">
                  <a href="%s" style="display:inline-block;background:#0066ff;color:#fff;text-decoration:none;
                  font-weight:700;font-size:14px;padding:12px 22px;border-radius:12px">Ir a mi perfil</a>
                </p>
                """.formatted(esc(dni), esc(profileUrl)),
                "FinanTech S.A. · Préstamos y control de pagos");
    }

    public static String paymentReminder(
            String nombre, String loanCode, String cuotaLabel, String monto, String payUrl) {
        return shell(
                "Recordatorio de cuota",
                "Hola " + esc(nombre) + ",",
                """
                <p style="margin:0 0 14px;font-size:15px;line-height:1.55;color:#334155">
                  Tienes una cuota pendiente en tu préstamo <strong>%s</strong>.
                </p>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                  style="margin:0 0 18px;border:1px solid #d7e3ef;border-radius:12px;overflow:hidden">
                  <tr style="background:#f1f7fc">
                    <td style="padding:10px 14px;font-size:12px;color:#64748b">Cuota</td>
                    <td style="padding:10px 14px;font-size:12px;color:#64748b;text-align:right">Monto</td>
                  </tr>
                  <tr>
                    <td style="padding:12px 14px;font-size:15px;color:#0a2540;font-weight:700">%s</td>
                    <td style="padding:12px 14px;font-size:15px;color:#0066ff;font-weight:700;text-align:right">%s</td>
                  </tr>
                </table>
                <p style="margin:0 0 8px;text-align:center">
                  <a href="%s" style="display:inline-block;background:#0066ff;color:#fff;text-decoration:none;
                  font-weight:700;font-size:14px;padding:12px 22px;border-radius:12px">Pagar cuota</a>
                </p>
                """.formatted(esc(loanCode), esc(cuotaLabel), esc(monto), esc(payUrl)),
                "Si ya pagaste, puedes ignorar este aviso.");
    }

    private static String shell(String title, String greeting, String bodyHtml, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>%s</title></head>
            <body style="margin:0;padding:0;background:#eef5fb;font-family:'Segoe UI',Arial,sans-serif">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#eef5fb;padding:24px 12px">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                    style="max-width:560px;background:#ffffff;border:1px solid #c9d9ea;border-radius:16px;overflow:hidden">
                    <tr>
                      <td style="background:linear-gradient(135deg,#0a2540,#1d4ed8);padding:22px 24px;color:#fff">
                        <p style="margin:0 0 6px;font-size:11px;letter-spacing:.14em;text-transform:uppercase;opacity:.85">FinanTech S.A.</p>
                        <h1 style="margin:0;font-size:22px;font-weight:700;line-height:1.25">%s</h1>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:24px">
                        <p style="margin:0 0 14px;font-size:15px;color:#0a2540;font-weight:600">%s</p>
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:14px 24px;background:#f7fbff;font-size:11px;color:#64748b;line-height:1.45">
                        %s
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(esc(title), esc(title), greeting, bodyHtml, esc(footer));
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
