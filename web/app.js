'use strict';

const $ = id => document.getElementById(id);
const admin = location.pathname === '/admin';
const statusLabels = { SAVED: 'محفوظ', APPLIED: 'تم التقديم', INTERVIEW: 'مقابلة', REJECTED: 'مرفوض', OFFER: 'عرض عمل' };
let jobPage = 1;
let applicationPage = 1;
let desktopPage = 1;
let searchTimer;
let searchVersion = 0;

async function api(path, data, options = {}) {
    if (data) options = { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(data) };
    const response = await fetch(path, options);
    if (response.status === 401 && admin) {
        location.replace('/admin');
        throw new Error('انتهت الجلسة. سجّل الدخول مجددًا.');
    }
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'تعذر تنفيذ الطلب');
    return result;
}

function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function report(error) { $('notice').textContent = error.message; }

function pager(id, result, callback) {
    const target = $(id);
    const previous = element('button', 'السابق', 'secondary');
    const next = element('button', 'التالي', 'secondary');
    previous.disabled = result.page <= 1;
    next.disabled = !result.hasMore;
    previous.onclick = () => callback(result.page - 1).catch(report);
    next.onclick = () => callback(result.page + 1).catch(report);
    target.replaceChildren(previous, element('span', 'صفحة ' + result.page), next);
}

function jobCard(job) {
    const node = element('article', undefined, 'card');
    node.append(element('span', job.company, 'tag'), element('h3', job.title), element('p', job.location, 'muted'));
    node.append(element('p', job.description.length > 150 ? job.description.slice(0, 150) + '…' : job.description));
    if (admin) node.append(element('p', job.active ? 'مفتوحة للتقديم' : 'مغلقة', 'muted'));
    const button = element('button', admin ? 'تعديل الوظيفة' : 'التفاصيل والتقديم');
    button.onclick = () => {
        if (admin) {
            const form = $('jobForm');
            for (const key of ['id', 'company', 'title', 'location', 'description']) form.elements[key].value = job[key];
            form.elements.active.checked = job.active;
            $('jobHeading').textContent = 'تعديل الوظيفة #' + job.id;
            form.scrollIntoView({ behavior: 'smooth' });
        } else {
            $('applyForm').reset();
            $('applyForm').elements.job.value = job.id;
            $('selectedTitle').textContent = job.title;
            $('selectedDetails').textContent = job.company + ' · ' + job.location;
            $('selectedDescription').textContent = job.description;
            $('formNotice').textContent = '';
            $('applyDialog').showModal();
        }
    };
    node.append(button);
    return node;
}

async function loadJobs(page = jobPage) {
    const version = ++searchVersion;
    const search = admin ? '' : $('search').value.trim();
    const result = await api((admin ? '/api/admin/jobs' : '/api/jobs') + '?page=' + page + '&search=' + encodeURIComponent(search));
    if (version !== searchVersion) return;
    jobPage = page;
    const target = $(admin ? 'adminJobs' : 'jobs');
    target.replaceChildren(...result.items.map(jobCard));
    if (!result.items.length) target.append(element('p', 'لا توجد وظائف متاحة تطابق البحث.'));
    pager(admin ? 'adminJobPager' : 'jobPager', result, loadJobs);
}

function statusSelect(row, desktop = false) {
    const select = element('select');
    select.setAttribute('aria-label', 'حالة الطلب #' + row.id);
    for (const [value, label] of Object.entries(statusLabels)) {
        if (!desktop && value === 'SAVED') continue;
        const option = element('option', label);
        option.value = value;
        select.append(option);
    }
    select.value = row.status;
    select.onchange = async () => {
        select.disabled = true;
        try {
            await api(desktop ? '/api/admin/desktop/status' : '/api/admin/status', { id: row.id, status: select.value });
            row.status = select.value;
            $('notice').textContent = 'تم تحديث الحالة.';
        } catch (error) { select.value = row.status; report(error); }
        finally { select.disabled = false; }
    };
    return select;
}

async function loadApplications(page = applicationPage) {
    const result = await api('/api/admin/applications?page=' + page);
    applicationPage = page;
    const cards = result.items.map(row => {
        const card = element('article', undefined, 'panel application');
        const details = element('div');
        details.append(element('h3', row.name), element('p', row.title + ' · ' + row.email), element('p', row.date, 'muted'));
        const download = element('a', 'تنزيل السيرة الذاتية', 'download');
        download.href = '/api/admin/cv/' + row.id;
        card.append(details, download, statusSelect(row));
        return card;
    });
    $('applications').replaceChildren(...cards);
    if (!cards.length) $('applications').append(element('p', 'لا توجد طلبات حتى الآن.'));
    pager('applicationPager', result, loadApplications);
}

async function loadDesktop(page = desktopPage) {
    const result = await api('/api/admin/desktop?page=' + page);
    desktopPage = page;
    const cards = result.items.map(row => {
        const form = element('form', undefined, 'panel');
        form.append(element('h3', 'سجل سطح المكتب #' + row.id));
        for (const [key, label] of Object.entries({ company: 'الشركة', title: 'المسمى', location: 'المكان' })) {
            const holder = element('label', label);
            const input = element('input');
            input.name = key; input.value = row[key]; input.required = true; input.maxLength = 200;
            holder.append(input); form.append(holder);
        }
        const save = element('button', 'حفظ التفاصيل');
        const actions = element('div', undefined, 'desktop-actions');
        actions.append(save, statusSelect(row, true)); form.append(actions);
        form.onsubmit = async event => {
            event.preventDefault(); save.disabled = true;
            try {
                await api('/api/admin/desktop/details', { ...Object.fromEntries(new FormData(form)), id: row.id });
                $('notice').textContent = 'تم حفظ سجل سطح المكتب.';
            } catch (error) { report(error); }
            finally { save.disabled = false; }
        };
        return form;
    });
    $('desktopRecords').replaceChildren(...cards);
    if (!cards.length) $('desktopRecords').append(element('p', 'لا توجد سجلات في برنامج سطح المكتب.'));
    pager('desktopPager', result, loadDesktop);
}

$('candidate').hidden = admin;
$('company').hidden = !admin;
$('logout').hidden = !admin;
if (admin) {
    document.querySelector('.hero h1').textContent = 'كل فرصة، وكل متقدم.';
    document.querySelector('.hero p').textContent = 'انشر الوظائف، راجع السير الذاتية، وتابع سجلات سطح المكتب.';
}

$('logout').onclick = async () => {
    try { await api('/api/logout', {}); location.replace('/admin'); } catch (error) { report(error); }
};
$('search').oninput = () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => loadJobs(1).catch(report), 250);
};
$('closeDialog').onclick = () => $('applyDialog').close();
$('newJob').onclick = () => {
    $('jobForm').reset(); $('jobForm').elements.id.value = '';
    $('jobHeading').textContent = 'نشر وظيفة جديدة';
};
$('jobForm').onsubmit = async event => {
    event.preventDefault();
    const form = event.target, button = form.querySelector('button');
    button.disabled = true;
    try {
        const data = Object.fromEntries(new FormData(form));
        data.active = String(form.elements.active.checked);
        await api('/api/admin/jobs', data);
        form.reset(); form.elements.id.value = '';
        $('jobHeading').textContent = 'نشر وظيفة جديدة';
        $('notice').textContent = 'تم حفظ الوظيفة.';
        await loadJobs(1);
    } catch (error) { report(error); }
    finally { button.disabled = false; }
};
$('applyForm').onsubmit = async event => {
    event.preventDefault();
    const form = event.target, file = form.elements.file.files[0];
    if (!file || file.size > 5 * 1024 * 1024 || !file.name.toLowerCase().endsWith('.pdf')) {
        $('formNotice').textContent = 'اختار ملف PDF بحد أقصى 5 MB.'; return;
    }
    $('submitApplication').disabled = true;
    try {
        const headers = { 'Content-Type': 'application/pdf' };
        const metadata = { job: form.elements.job.value, name: form.elements.name.value, email: form.elements.email.value, filename: file.name };
        for (const [key, value] of Object.entries(metadata)) headers['X-Application-' + key] = encodeURIComponent(value);
        const result = await api('/api/apply', null, { method: 'POST', headers, body: file });
        $('applyDialog').close();
        $('notice').textContent = 'تم إرسال طلبك. رقم الطلب: ' + result.id + '. احفظ كود المتابعة الخاص: ' + result.trackingToken;
        $('trackForm').elements.token.value = result.trackingToken;
        form.reset();
    } catch (error) { $('formNotice').textContent = error.message; }
    finally { $('submitApplication').disabled = false; }
};
$('trackForm').onsubmit = async event => {
    event.preventDefault();
    const form = event.target, button = form.querySelector('button');
    button.disabled = true;
    try {
        const result = await api('/api/track', { token: form.elements.token.value.trim() });
        $('trackResult').textContent = result.title + ' · ' + result.company + '\nالحالة: ' + statusLabels[result.status];
    } catch (error) { $('trackResult').textContent = error.message; }
    finally { button.disabled = false; }
};

loadJobs().catch(report);
if (admin) { loadApplications().catch(report); loadDesktop().catch(report); }
