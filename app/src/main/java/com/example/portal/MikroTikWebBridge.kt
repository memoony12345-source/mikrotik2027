package com.example.portal

import android.webkit.JavascriptInterface

object MikroTikWebBridge {

    const val TEST_PORTAL_URL = "http://mikrotik-test.local/login"

    /**
     * Injects a lightweight DOM analyzer + MutationObserver that identifies MikroTik
     * voucher/username fields, optional password fields, submit buttons, and logged-in status pages.
     */
    val DOM_INSPECTOR_SCRIPT: String = """
        (function() {
            if (window.__mikrotik2027Installed) {
                if (window.__mikrotik2027ScanDom) window.__mikrotik2027ScanDom();
                return;
            }
            window.__mikrotik2027Installed = true;

            function isVisible(el) {
                if (!el) return false;
                var style = window.getComputedStyle(el);
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                var rect = el.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0;
            }

            function findVoucherInput() {
                var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
                var best = null;
                var bestScore = -1;
                var keywords = ['username', 'user', 'voucher', 'code', 'pin', 'card', 'serial', 'ticket', 'pass', 'كرت', 'بطاقة', 'رمز', 'كود', 'المستخدم', 'دخول'];

                inputs.forEach(function(inp) {
                    var t = (inp.type || 'text').toLowerCase();
                    if (t === 'hidden' || t === 'submit' || t === 'button' || t === 'checkbox' || t === 'radio') return;
                    if (!isVisible(inp)) return;

                    var meta = ((inp.name || '') + ' ' + (inp.id || '') + ' ' + (inp.placeholder || '') + ' ' + (inp.getAttribute('aria-label') || '') + ' ' + (inp.className || '')).toLowerCase();
                    var score = 1;
                    if (t === 'text' || t === 'tel' || t === 'number' || t === '') score += 5;
                    if (t === 'password') score += 2;
                    keywords.forEach(function(k) {
                        if (meta.indexOf(k) !== -1) score += 10;
                    });
                    if ((inp.name || '').toLowerCase() === 'username') score += 20;
                    if ((inp.name || '').toLowerCase().indexOf('voucher') !== -1 || (inp.name || '').toLowerCase().indexOf('code') !== -1) score += 20;
                    if (score > bestScore) {
                        bestScore = score;
                        best = inp;
                    }
                });
                return best;
            }

            function findPasswordInput(voucherInput) {
                var pwds = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'));
                for (var i = 0; i < pwds.length; i++) {
                    if (pwds[i] !== voucherInput && isVisible(pwds[i])) return pwds[i];
                }
                return null;
            }

            function findSubmitButton(voucherInput) {
                var form = voucherInput ? voucherInput.form : document.querySelector('form');
                var scope = form || document;
                var candidates = Array.prototype.slice.call(scope.querySelectorAll('button, input[type="submit"], input[type="button"], a.btn, a.button'));
                var loginWords = ['login', 'submit', 'connect', 'sign in', 'ok', 'go', 'دخول', 'تسجيل', 'اتصال', 'تفعيل', 'موافق'];

                for (var i = 0; i < candidates.length; i++) {
                    var el = candidates[i];
                    if (!isVisible(el)) continue;
                    var text = ((el.innerText || '') + ' ' + (el.value || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
                    if ((el.type || '').toLowerCase() === 'submit') return el;
                    for (var j = 0; j < loginWords.length; j++) {
                        if (text.indexOf(loginWords[j]) !== -1) return el;
                    }
                }
                if (form) {
                    var anySubmit = form.querySelector('button, input[type="submit"]');
                    if (anySubmit) return anySubmit;
                }
                return null;
            }

            window.__mikrotik2027ScanDom = function() {
                try {
                    var vInput = findVoucherInput();
                    var pInput = findPasswordInput(vInput);
                    var sBtn = findSubmitButton(vInput);
                    var bodyText = (document.body ? document.body.innerText : '').toLowerCase();
                    var urlLower = (window.location.href || '').toLowerCase();

                    var isStatusPage = !vInput && (
                        urlLower.indexOf('status') !== -1 ||
                        bodyText.indexOf('log off') !== -1 ||
                        bodyText.indexOf('logout') !== -1 ||
                        bodyText.indexOf('bytes up') !== -1 ||
                        bodyText.indexOf('uptime') !== -1 ||
                        bodyText.indexOf('تسجيل الخروج') !== -1 ||
                        bodyText.indexOf('متصل بنجاح') !== -1
                    );

                    var hint = '';
                    if (vInput) {
                        hint = vInput.placeholder || vInput.name || vInput.id || 'حقل الكرت';
                    }

                    if (window.MikrotikBridge && window.MikrotikBridge.onDomState) {
                        window.MikrotikBridge.onDomState(
                            window.location.href || '',
                            document.title || '',
                            Boolean(vInput),
                            Boolean(pInput),
                            Boolean(sBtn),
                            String(hint),
                            Boolean(isStatusPage)
                        );
                    }
                } catch (e) {}
            };

            window.__mikrotik2027FillCode = function(code, mirrorPassword, autoSubmit) {
                try {
                    var vInput = findVoucherInput();
                    if (!vInput) return 'NO_INPUT_FOUND';

                    var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    vInput.focus();
                    if (nativeSetter) {
                        nativeSetter.call(vInput, code);
                    } else {
                        vInput.value = code;
                    }
                    vInput.dispatchEvent(new Event('input', { bubbles: true }));
                    vInput.dispatchEvent(new Event('change', { bubbles: true }));
                    vInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));

                    var pInput = findPasswordInput(vInput);
                    if (pInput && mirrorPassword) {
                        if (nativeSetter) {
                            nativeSetter.call(pInput, code);
                        } else {
                            pInput.value = code;
                        }
                        pInput.dispatchEvent(new Event('input', { bubbles: true }));
                        pInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    if (!autoSubmit) return 'FILLED_ONLY';

                    var sBtn = findSubmitButton(vInput);
                    if (sBtn) {
                        setTimeout(function() { sBtn.click(); }, 180);
                        return 'FILLED_AND_CLICKED';
                    }

                    if (typeof window.doLogin === 'function') {
                        setTimeout(function() { window.doLogin(); }, 180);
                        return 'FILLED_AND_DOLOGIN';
                    }

                    if (vInput.form) {
                        setTimeout(function() { vInput.form.submit(); }, 180);
                        return 'FILLED_AND_FORM_SUBMITTED';
                    }
                    return 'FILLED_NO_SUBMIT_TARGET';
                } catch (err) {
                    return 'ERROR:' + err.message;
                }
            };

            var observer = new MutationObserver(function() {
                window.__mikrotik2027ScanDom();
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true, attributes: true });
            }
            window.__mikrotik2027ScanDom();
        })();
    """.trimIndent()

    fun buildFillAndSubmitScript(
        code: String,
        mirrorPassword: Boolean,
        autoSubmit: Boolean,
    ): String {
        val escaped = code
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
        return "$DOM_INSPECTOR_SCRIPT\nwindow.__mikrotik2027FillCode('$escaped', $mirrorPassword, $autoSubmit);"
    }

    /**
     * Built-in local MikroTik Hotspot simulation page so users can test DOM detection,
     * CameraX/Gallery OCR voucher recognition, auto-fill, and auto-submit even offline or on emulator.
     */
    val TEST_PORTAL_HTML: String = """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>بوابة شبكة المايكروتك التجريبية</title>
          <style>
            body {
              margin: 0; font-family: sans-serif;
              background: #000000;
              color: #faf6ec; min-height: 100vh; display: flex;
              align-items: center; justify-content: center; padding: 20px; box-sizing: border-box;
            }
            .card {
              width: 100%; max-width: 380px; background: #0d0d10;
              border: 1px solid #332912; border-radius: 20px; padding: 24px;
              box-shadow: 0 20px 40px rgba(0,0,0,0.65);
            }
            .badge {
              display: inline-block; background: rgba(245,196,81,0.14);
              color: #f5c451; padding: 4px 12px; border-radius: 999px; font-size: 12px;
              margin-bottom: 12px;
            }
            h1 { margin: 0 0 6px; font-size: 20px; }
            p { margin: 0 0 18px; color: #a39b8b; font-size: 13px; line-height: 1.6; }
            label { display: block; font-size: 13px; margin-bottom: 6px; color: #e5dec9; }
            input[type="text"] {
              width: 100%; box-sizing: border-box; padding: 14px; border-radius: 12px;
              border: 1px solid #4d3d19; background: #000000; color: #f5c451;
              font-size: 18px; letter-spacing: 2px; text-align: center; direction: ltr;
              margin-bottom: 14px; outline: none;
            }
            input[type="text"]:focus { border-color: #f5c451; }
            button {
              width: 100%; padding: 14px; border: 0; border-radius: 12px;
              background: #f5c451; color: #000000; font-size: 16px; font-weight: bold;
              cursor: pointer;
            }
            .status-box {
              display: none; margin-top: 16px; padding: 14px; border-radius: 12px;
              background: rgba(16,185,129,0.14); border: 1px solid #10b981; color: #6ee7b7;
              text-align: center; font-size: 14px;
            }
            .error-box {
              display: none; margin-top: 12px; padding: 10px; border-radius: 10px;
              background: rgba(239,68,68,0.14); border: 1px solid #ef4444; color: #fca5a5;
              font-size: 13px; text-align: center;
            }
          </style>
        </head>
        <body>
          <div class="card" id="loginCard">
            <span class="badge">MikroTik Hotspot • وضع التجربة</span>
            <h1>تسجيل الدخول للشبكة</h1>
            <p>أدخل رقم كرت الإنترنت (من 6 إلى 12 خانة) أو استخدم ماسح تطبيق mikrotik 2027.</p>
            <form name="login" id="loginForm" onsubmit="return handleMikrotikLogin(event);">
              <label for="voucherField">رقم الكرت (Voucher Code)</label>
              <input id="voucherField" name="username" type="text" placeholder="أدخل رمز الكرت" autocomplete="off" />
              <button type="submit" id="loginBtn">تسجيل الدخول الآن</button>
            </form>
            <div class="error-box" id="errBox">يرجى إدخال كود كرت صحيح من 6 إلى 12 خانة.</div>
            <div class="status-box" id="okBox"></div>
          </div>
          <script>
            function handleMikrotikLogin(e) {
              e.preventDefault();
              var code = (document.getElementById('voucherField').value || '').trim();
              var err = document.getElementById('errBox');
              var ok = document.getElementById('okBox');
              if (code.length < 6 || code.length > 12) {
                err.style.display = 'block';
                ok.style.display = 'none';
                return false;
              }
              err.style.display = 'none';
              document.getElementById('loginForm').style.display = 'none';
              ok.style.display = 'block';
              ok.innerHTML = '<strong>متصل بنجاح!</strong><br/>تم تفعيل الكرت: <span style="font-family:monospace;color:#fff;">' + code + '</span><br/><br/><button type="button" onclick="location.reload()">تسجيل الخروج / تجربة كرت آخر</button>';
              if (window.__mikrotik2027ScanDom) setTimeout(window.__mikrotik2027ScanDom, 100);
              return false;
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

class MikroTikJsInterface(
    private val onDomStateUpdated: (
        url: String,
        title: String,
        hasVoucherField: Boolean,
        hasPasswordField: Boolean,
        hasSubmitButton: Boolean,
        fieldHint: String,
        isStatusPage: Boolean,
    ) -> Unit,
) {
    @JavascriptInterface
    fun onDomState(
        url: String,
        title: String,
        hasVoucherField: Boolean,
        hasPasswordField: Boolean,
        hasSubmitButton: Boolean,
        fieldHint: String,
        isStatusPage: Boolean,
    ) {
        onDomStateUpdated(url, title, hasVoucherField, hasPasswordField, hasSubmitButton, fieldHint, isStatusPage)
    }
}
