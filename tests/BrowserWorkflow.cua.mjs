/** Replayable end-to-end UI test for a Codex cua_repl Tab, using a synthetic PDF fixture.
 * Load/paste the function into cua_repl, then call runBrowserWorkflow(tab, {username,password}, absolutePdfPath).
 * Requires the local TEST database demo server; never use with a production deployment.
 */
export async function runBrowserWorkflow(tab, credentials, pdfPath) {
    const title = 'Browser regression test ' + Date.now();
    const email = 'browser-' + Date.now() + '@example.test';
    await tab.goto('http://localhost:8080/admin');
    await tab.getAXState();
    if (await tab.playwright.getByRole('button', { name: 'تسجيل الخروج', exact: true }).isVisible()) {
        await tab.playwright.getByRole('button', { name: 'تسجيل الخروج', exact: true }).click();
        await tab.getAXState();
    }
    await tab.playwright.getByLabel('Username', { exact: true }).fill(credentials.username);
    await tab.playwright.getByLabel('Password', { exact: true }).fill(credentials.password);
    await tab.playwright.getByRole('button', { name: 'Sign in', exact: true }).click();
    await tab.playwright.getByRole('heading', { name: 'إدارة التوظيف', exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByLabel('الشركة', { exact: true }).fill('Browser Regression Demo');
    await tab.playwright.getByLabel('المسمى الوظيفي', { exact: true }).fill(title);
    await tab.playwright.getByLabel('المكان', { exact: true }).fill('Test only');
    await tab.playwright.getByLabel('الوصف والمتطلبات', { exact: true }).fill('Synthetic regression fixture; not a real vacancy.');
    await tab.playwright.getByRole('button', { name: 'حفظ الوظيفة', exact: true }).click();
    await tab.playwright.getByText('تم حفظ الوظيفة.', { exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('link', { name: 'الوظائف المتاحة', exact: true }).click();
    await tab.playwright.getByRole('heading', { name: title, exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('article').filter({ hasText: title }).getByRole('button', { name: 'التفاصيل والتقديم', exact: true }).click();
    await tab.getAXState();
    await tab.playwright.getByLabel('الاسم بالكامل', { exact: true }).fill('Synthetic Browser Candidate');
    await tab.playwright.getByLabel('البريد الإلكتروني', { exact: true }).fill(email);
    const chooserPromise = tab.playwright.waitForEvent('filechooser', { timeoutMs: 10000 });
    await tab.playwright.getByLabel('السيرة الذاتية PDF — بحد أقصى 5 MB', { exact: true }).click();
    const chooser = await chooserPromise;
    await chooser.setFiles([pdfPath]);
    await tab.getAXState();
    await tab.playwright.getByRole('button', { name: 'إرسال الطلب', exact: true }).click();
    await tab.playwright.getByText(/تم إرسال طلبك\. رقم الطلب:/).waitFor({ state: 'visible' });
    await tab.getAXState();
    const confirmation = await tab.playwright.getByText(/تم إرسال طلبك\. رقم الطلب:/).innerText();
    const id = confirmation.match(/رقم الطلب: (\d+)/)[1];
    const token = await tab.playwright.getByLabel('كود المتابعة الخاص', { exact: true }).evaluate(input => input.value);
    await tab.playwright.getByRole('button', { name: 'عرض الحالة', exact: true }).click();
    await tab.playwright.getByText(/الحالة: تم التقديم/).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('link', { name: 'إدارة الشركة', exact: true }).click();
    await tab.playwright.getByRole('combobox', { name: 'حالة الطلب #' + id, exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('combobox', { name: 'حالة الطلب #' + id, exact: true }).selectOption('INTERVIEW');
    await tab.playwright.getByText('تم تحديث الحالة.', { exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('button', { name: 'تسجيل الخروج', exact: true }).click();
    await tab.playwright.getByRole('heading', { name: 'Company login', exact: true }).waitFor({ state: 'visible' });
    await tab.getAXState();
    await tab.playwright.getByRole('link', { name: 'Back to applicant view', exact: true }).click();
    await tab.getAXState();
    await tab.playwright.getByLabel('كود المتابعة الخاص', { exact: true }).fill(token);
    await tab.playwright.getByRole('button', { name: 'عرض الحالة', exact: true }).click();
    await tab.playwright.getByText(/الحالة: مقابلة/).waitFor({ state: 'visible' });
    await tab.getAXState();
    return { passed: true, applicationId: id, workflow: 'login, publish, upload, apply, track, update status, logout, track while logged out' };
}
