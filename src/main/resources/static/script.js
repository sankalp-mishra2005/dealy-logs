// BSP Delay Management System - JavaScript Logic with REST API Integration

// Global lists populated dynamically from server
let activeShifts = [];
let allShops = [];
let allShopAreas = [];
let allDelayTypes = [];
let activeShopId = 1; // Plate Mill

// Loaded delay logs list for the active entry page
let loadedDelayLogs = [];

// Chart.js instances to avoid overlapping canvas redraw errors
let chartDailyTrend = null;
let chartTypeDistribution = null;
let chartMonthlyTrend = null;
let chartTopDelays = null;

// ---- 1. Helper Utilities ----

// Convert HH:MM time string to total minutes from midnight
function timeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    return parseInt(parts[0]) * 60 + parseInt(parts[1]);
}

// Convert minutes to HH:MM format
function minutesToHHMM(totalMinutes) {
    const hrs = Math.floor(totalMinutes / 60);
    const mins = totalMinutes % 60;
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}`;
}

// Calculate duration and check for overnight crossing
function calculateMinutesDuration(startTime, endTime) {
    if (!startTime || !endTime) return 0;
    let startMin = timeToMinutes(startTime);
    let endMin = timeToMinutes(endTime);
    let diff = endMin - startMin;
    if (diff < 0) {
        diff += 24 * 60; // Crosses midnight
    }
    return diff;
}




// ---- 2. Dropdown Rendering & Custom Searchable Dropdown Management ----
function initSearchableDropdowns() {
    const wrappers = document.querySelectorAll('.custom-select-wrapper');
    
    wrappers.forEach(wrapper => {
        const trigger = wrapper.querySelector('.custom-select-trigger');
        const searchInput = wrapper.querySelector('.custom-select-search-input');
        const optionsList = wrapper.querySelector('.custom-select-options');
        
        // Toggle dropdown open state
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            const isOpen = wrapper.classList.contains('open');
            
            // Close all dropdowns first
            document.querySelectorAll('.custom-select-wrapper').forEach(w => w.classList.remove('open'));
            
            if (!isOpen) {
                wrapper.classList.add('open');
                if (searchInput) {
                    searchInput.value = '';
                    searchInput.focus();
                    filterOptions(wrapper, '');
                }
            }
        });
        
        // Search functionality
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                filterOptions(wrapper, e.target.value);
            });
            
            searchInput.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }
        
        // Option selection behavior
        optionsList.addEventListener('click', (e) => {
            const option = e.target.closest('.custom-select-option');
            if (option) {
                selectOption(wrapper, option);
            }
        });
    });
}

function filterOptions(wrapper, query) {
    const options = wrapper.querySelectorAll('.custom-select-option');
    const optionsList = wrapper.querySelector('.custom-select-options');
    let matched = 0;
    
    options.forEach(opt => {
        const txt = opt.textContent.toLowerCase();
        if (txt.includes(query.toLowerCase())) {
            opt.style.display = 'block';
            matched++;
        } else {
            opt.style.display = 'none';
        }
    });
    
    let noResults = optionsList.querySelector('.custom-select-no-results');
    if (matched === 0) {
        if (!noResults) {
            noResults = document.createElement('div');
            noResults.className = 'custom-select-no-results';
            noResults.textContent = 'No results matched';
            optionsList.appendChild(noResults);
        }
    } else {
        if (noResults) {
            noResults.remove();
        }
    }
}

function selectOption(wrapper, optionElement) {
    const triggerText = wrapper.querySelector('.custom-select-text');
    const options = wrapper.querySelectorAll('.custom-select-option');
    
    options.forEach(opt => opt.classList.remove('selected'));
    optionElement.classList.add('selected');
    
    const val = optionElement.dataset.value;
    wrapper.dataset.value = val;
    triggerText.textContent = optionElement.textContent;
    
    wrapper.classList.remove('open');
    
    // Trigger custom change event for reactivity
    const event = new CustomEvent('change', { detail: { value: val } });
    wrapper.dispatchEvent(event);
}

// Close dropdowns when clicking outside
document.addEventListener('click', () => {
    document.querySelectorAll('.custom-select-wrapper').forEach(w => w.classList.remove('open'));
});

// Helper to check/set selected value on a custom dropdown
function setCustomDropdownValue(wrapperId, value) {
    const wrapper = document.getElementById(wrapperId);
    if (!wrapper) return;
    
    const triggerText = wrapper.querySelector('.custom-select-text');
    const options = wrapper.querySelectorAll('.custom-select-option');
    
    let found = false;
    options.forEach(opt => {
        if (opt.dataset.value === String(value)) {
            options.forEach(o => o.classList.remove('selected'));
            opt.classList.add('selected');
            wrapper.dataset.value = value;
            triggerText.textContent = opt.textContent;
            found = true;
        }
    });
    
    if (!found) {
        wrapper.dataset.value = '';
        triggerText.textContent = 'Select...';
    }
}


// ---- 3. Fetch Master Dimensions & Initialize Dropdowns ----
async function fetchMetadata() {
    try {
        // 1. Fetch Shops
        const shopsRes = await fetch('/api/shops');
        const shopsData = await shopsRes.json();
        if (shopsData.success) {
            allShops = shopsData.data;
            populateShopDropdowns(allShops);
        }

        // 2. Fetch Shop Areas
        const areasRes = await fetch(`/api/shop-areas/${activeShopId}`);
        const areasData = await areasRes.json();
        if (areasData.success) {
            allShopAreas = areasData.data;
            populateAreaDropdowns(allShopAreas);
            renderOperationalInputBoxes(allShopAreas);
        }

        // 3. Fetch Delay Types
        const typesRes = await fetch('/api/delay-types');
        const typesData = await typesRes.json();
        if (typesData.success) {
            allDelayTypes = typesData.data;
            populateDelayTypeDropdowns(allDelayTypes);
        }
    } catch (e) {
        console.error("Error fetching reference metadata:", e);
        alert("Failed to connect to the backend server. Please verify Spring Boot status.");
    }
}

function populateShopDropdowns(shops) {
    const entryOptions = document.getElementById('entry-shop-options');
    entryOptions.innerHTML = shops.map(shop => 
        `<div class="custom-select-option" data-value="${shop.shopId}">${shop.shopName}</div>`
    ).join('');

    const reportShopSelect = document.getElementById('report-shop-filter');
    reportShopSelect.innerHTML = shops.map(shop => 
        `<option value="${shop.shopId}">${shop.shopName}</option>`
    ).join('');
}

function populateAreaDropdowns(areas) {
    const entryOptions = document.getElementById('entry-area-options');
    entryOptions.innerHTML = areas.map(area => 
        `<div class="custom-select-option" data-value="${area.areaId}">${area.areaName}</div>`
    ).join('');

    // Report Page standard select dropdown
    const reportAreaSelect = document.getElementById('report-area-filter');
    reportAreaSelect.innerHTML = '<option value="All">All Areas</option>' + 
        areas.map(area => `<option value="${area.areaId}">${area.areaName}</option>`).join('');
}

function populateDelayTypeDropdowns(types) {
    const entryOptions = document.getElementById('entry-type-options');
    entryOptions.innerHTML = types.map(type => 
        `<div class="custom-select-option" data-value="${type.delayTypeId}">${type.typeName} [${type.delayGroup}]</div>`
    ).join('');

    const reportTypeSelect = document.getElementById('report-type-filter');
    reportTypeSelect.innerHTML = '<option value="All">All Types</option>' + 
        types.map(t => `<option value="${t.delayTypeId}">${t.typeName}</option>`).join('');
}

async function onEntryShopChange(shopId) {
    activeShopId = parseInt(shopId);
    
    // Fetch and reload areas for this shop
    const areasRes = await fetch(`/api/shop-areas/${activeShopId}`);
    const areasData = await areasRes.json();
    if (areasData.success) {
        allShopAreas = areasData.data;
        populateAreaDropdowns(allShopAreas);
        setCustomDropdownValue('entry-area-dropdown', '');
    }

    const delayTypeId = document.getElementById('entry-type-dropdown').dataset.value;
    if (delayTypeId) {
        onEntryTypeChange(delayTypeId);
    } else {
        hideReasonDropdown();
    }
    
    renderOperationalInputBoxes(allShopAreas);
    loadDayMetrics();
}

async function onEntryTypeChange(delayTypeId) {
    if (!delayTypeId || !activeShopId) {
        hideReasonDropdown();
        return;
    }
    
    try {
        const res = await fetch(`/api/delay-reasons?shopId=${activeShopId}&delayTypeId=${delayTypeId}`);
        const data = await res.json();
        if (data.success && data.data && data.data.length > 0) {
            populateReasonDropdown(data.data);
            showReasonDropdown();
        } else {
            hideReasonDropdown();
        }
    } catch (e) {
        console.error("Error fetching delay reasons:", e);
        hideReasonDropdown();
    }
}

function populateReasonDropdown(reasons) {
    const entryReasonOptions = document.getElementById('entry-reason-options');
    entryReasonOptions.innerHTML = reasons.map(reason => 
        `<div class="custom-select-option" data-value="${reason.reasonId}">${reason.reasonName}</div>`
    ).join('');
    setCustomDropdownValue('entry-reason-dropdown', '');
}

function showReasonDropdown() {
    document.getElementById('entry-reason-group').style.display = 'block';
}

function hideReasonDropdown() {
    document.getElementById('entry-reason-group').style.display = 'none';
    const dropdown = document.getElementById('entry-reason-dropdown');
    if (dropdown) dropdown.dataset.value = '';
}

async function onReportShopChange() {
    const shopId = document.getElementById('report-shop-filter').value;
    
    const areasRes = await fetch(`/api/shop-areas/${shopId}`);
    const areasData = await areasRes.json();
    if (areasData.success) {
        const areas = areasData.data;
        const reportAreaSelect = document.getElementById('report-area-filter');
        reportAreaSelect.innerHTML = '<option value="All">All Areas</option>' + 
            areas.map(area => `<option value="${area.areaId}">${area.areaName}</option>`).join('');
    }
    
    generateDPRReport();
}

function renderOperationalInputBoxes(areas) {
    const container = document.getElementById('operational-inputs-container');
    container.innerHTML = areas.map(area => {
        let defaultProd = 1000;
        if (area.areaCode === 'MILL') defaultProd = 4000;
        else if (area.areaCode === 'NORM') defaultProd = 600;
        else if (area.areaCode === 'SMS_2') defaultProd = 3000;
        else if (area.areaCode === 'SMS_3') defaultProd = 3500;
        
        return `
            <div class="config-box" style="margin-bottom: 1rem;" data-area-id="${area.areaId}" data-area-code="${area.areaCode}">
                <h4>${area.areaName} Metrics</h4>
                <div class="form-group" style="margin-bottom: 0.75rem;">
                    <label style="font-size: 0.75rem;">Available Hours</label>
                    <input type="number" class="metric-avail" min="0" max="24" step="0.1" value="24.0" style="height: 34px; font-size: 0.85rem;">
                </div>
                <div class="form-group">
                    <label style="font-size: 0.75rem;">Production Tonnage (T)</label>
                    <input type="number" class="metric-prod" min="0" value="${defaultProd}" style="height: 34px; font-size: 0.85rem;">
                </div>
            </div>
        `;
    }).join('');
}

// ---- 4. Interactive Time & Auto-Shift Calculations ----
function onTimeChange() {
    const startTimeVal = document.getElementById('entry-start-time').value;
    const endTimeVal = document.getElementById('entry-end-time').value;

    if (startTimeVal && endTimeVal) {
        const totalMinutes = calculateMinutesDuration(startTimeVal, endTimeVal);
        document.getElementById('entry-duration').value = minutesToHHMM(totalMinutes);
    } else {
        document.getElementById('entry-duration').value = '00:00';
    }
}


// ---- 5. Delay Event Form Actions (Create, Edit, Delete) ----
async function saveDelayEvent(event) {
    event.preventDefault();
    
    const logId = document.getElementById('entry-log-id').value;
    const date = document.getElementById('entry-date').value;
    const areaId = document.getElementById('entry-area-dropdown').dataset.value;
    const delayTypeId = document.getElementById('entry-type-dropdown').dataset.value;
    const reasonId = document.getElementById('entry-reason-dropdown').dataset.value;
    const startTime = document.getElementById('entry-start-time').value;
    const endTime = document.getElementById('entry-end-time').value;
    const shift = document.getElementById('entry-shift').value;
    const remarks = document.getElementById('entry-remarks').value;
    
    const payload = {
        areaId: parseInt(areaId),
        delayTypeId: parseInt(delayTypeId),
        reasonId: reasonId ? parseInt(reasonId) : null,
        logDate: date,
        startTime: startTime,
        endTime: endTime,
        shift: shift,
        remarks: remarks
    };

    try {
        let res;
        if (logId) {
            // Edit update mode
            res = await fetch(`/api/delay-log/${logId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        } else {
            // New log mode
            res = await fetch('/api/delay-log', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        }

        const data = await res.json();
        if (data.success) {
            alert(logId ? "Delay event updated successfully!" : "Delay event logged successfully in Oracle database!");
            clearEntryForm();
            renderEnteredRecordsTable();
        } else {
            alert("Failed to log delay event: " + data.message);
        }
    } catch (e) {
        console.error("Error saving delay event:", e);
        alert("Server communication error occurred.");
    }
}

function clearEntryForm() {
    document.getElementById('entry-log-id').value = '';
    document.getElementById('entry-start-time').value = '';
    document.getElementById('entry-end-time').value = '';
    document.getElementById('entry-duration').value = '';
    document.getElementById('entry-remarks').value = '';
    document.getElementById('entry-shift').value = 'A Shift';
    setCustomDropdownValue('entry-area-dropdown', '');
    setCustomDropdownValue('entry-type-dropdown', '');
    hideReasonDropdown();
}

async function renderEnteredRecordsTable() {
    const filterDate = document.getElementById('entry-date').value;
    const tableBody = document.getElementById('entered-records-body');
    tableBody.innerHTML = '';
    
    if (!filterDate) return;

    try {
        const fetchPromises = allShopAreas.map(area => fetch(`/api/delay-log?areaId=${area.areaId}&date=${filterDate}`));
        const responses = await Promise.all(fetchPromises);
        
        let records = [];
        for (const res of responses) {
            const apiRes = await res.json();
            if (apiRes.success && apiRes.data) {
                records = records.concat(apiRes.data);
            }
        }

        loadedDelayLogs = records; // save references for offline edit loads

        if (records.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="text-center" style="color: var(--text-secondary); padding: 2rem;">
                        No delays logged for this date (${filterDate}).
                    </td>
                </tr>
            `;
            return;
        }

        // Sort by ID descending
        records.sort((a, b) => b.logId - a.logId);

        records.forEach(rec => {
            const tr = document.createElement('tr');
            let areaBadge = 'badge-mill';
            if (rec.areaId === 2) areaBadge = 'badge-norm';
            else if (rec.areaId === 3) areaBadge = 'badge-sms2';
            else if (rec.areaId === 4) areaBadge = 'badge-sms3';
            
            const typeClass = `badge-${rec.delayGroup.toLowerCase().replace('/', '')}`;
            const reasonHtml = rec.reasonName ? `<br><small style="color:var(--text-secondary); font-style:italic;">Reason: ${rec.reasonName}</small>` : '';
            
            tr.innerHTML = `
                <td><strong>${rec.logDate}</strong></td>
                <td><span style="font-weight:600; color:var(--primary);">${rec.shift || 'A Shift'}</span></td>
                <td><span class="badge ${areaBadge}">${rec.areaName}</span></td>
                <td>
                    <span class="badge badge-delay ${typeClass}">${rec.typeName}</span>
                    ${reasonHtml}
                </td>
                <td class="text-center" style="font-size:0.82rem; font-family:monospace;">${rec.startTime}-${rec.endTime}</td>
                <td class="text-right"><strong>${rec.durationHHMM}</strong></td>
                <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${rec.remarks}">${rec.remarks}</td>
                <td class="text-center">
                    <button class="btn btn-sm btn-info" onclick="viewDelayEvent(${rec.logId})" style="padding: 2px 6px; font-size: 0.75rem;">View</button>
                    <button class="btn btn-sm btn-warning" onclick="editDelayEvent(${rec.logId})" style="padding: 2px 6px; font-size: 0.75rem;">Edit</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteDelayEvent(${rec.logId})" style="padding: 2px 6px; font-size: 0.75rem;">Delete</button>
                </td>
            `;
            tableBody.appendChild(tr);
        });
    } catch (e) {
        console.error("Error loading entered logs table:", e);
    }
}

async function editDelayEvent(id) {
    const record = loadedDelayLogs.find(rec => rec.logId === id);
    if (!record) {
        alert("Record data not found.");
        return;
    }

    // Scroll to form and populate fields
    document.getElementById('entry-log-id').value = record.logId;
    document.getElementById('entry-date').value = record.logDate;
    
    // Set searchable dropdown selections
    setCustomDropdownValue('entry-area-dropdown', record.areaId);
    setCustomDropdownValue('entry-type-dropdown', record.delayTypeId);
    
    if (record.reasonId) {
        await onEntryTypeChange(record.delayTypeId);
        setCustomDropdownValue('entry-reason-dropdown', record.reasonId);
    } else {
        hideReasonDropdown();
    }
    
    document.getElementById('entry-start-time').value = record.startTime || '00:00';
    document.getElementById('entry-end-time').value = record.endTime || '00:00';
    document.getElementById('entry-remarks').value = record.remarks;
    
    document.getElementById('entry-shift').value = record.shift || 'A Shift';

    // Recalculate duration display
    onTimeChange();
    
    // Scroll smoothly to form container
    document.getElementById('delay-entry-form').scrollIntoView({ behavior: 'smooth' });
}

function viewDelayEvent(id) {
    const record = loadedDelayLogs.find(rec => rec.logId === id);
    if (!record) return;

    const modal = document.getElementById('view-detail-modal');
    const modalBody = document.getElementById('modal-body-content');
    
    let areaBadge = 'badge-mill';
    if (record.areaId === 2) areaBadge = 'badge-norm';
    else if (record.areaId === 3) areaBadge = 'badge-sms2';
    else if (record.areaId === 4) areaBadge = 'badge-sms3';
    
    const typeClass = `badge-${record.delayGroup.toLowerCase().replace('/', '')}`;
    const reasonRow = record.reasonName ? `<div><strong>Delay Reason:</strong> ${record.reasonName}</div>` : '';

    modalBody.innerHTML = `
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-bottom:1.5rem;">
            <div><strong>Log Date:</strong> ${record.logDate}</div>
            <div><strong>Shift:</strong> ${record.shift || 'A Shift'}</div>
            <div><strong>Shop Area:</strong> <span class="badge ${areaBadge}">${record.areaName}</span></div>
            <div><strong>Delay Type:</strong> <span class="badge badge-delay ${typeClass}">${record.typeName} [${record.delayGroup}]</span></div>
            ${reasonRow}
            <div><strong>Time Interval:</strong> ${record.startTime} to ${record.endTime}</div>
            <div><strong>Logged Duration:</strong> ${record.durationHHMM} (${record.durationMinutes || 0} minutes)</div>
        </div>
        <div style="background-color:var(--card-bg-secondary); padding:1rem; border-radius:6px; border:1px solid var(--border-color);">
            <strong style="display:block; margin-bottom:0.5rem; color:var(--primary);">Root Cause Analysis / Remarks:</strong>
            <p style="margin:0; line-height:1.5; font-size:0.88rem; white-space:pre-wrap;">${record.remarks}</p>
        </div>
    `;
    modal.classList.add('active');
}

function closeViewModal() {
    document.getElementById('view-detail-modal').classList.remove('active');
}

async function deleteDelayEvent(id) {
    if (confirm("Are you sure you want to delete this delay event log?")) {
        try {
            const res = await fetch(`/api/delay-log/${id}`, { method: 'DELETE' });
            const data = await res.json();
            if (data.success) {
                renderEnteredRecordsTable();
            } else {
                alert("Failed to delete record: " + data.message);
            }
        } catch (e) {
            console.error("Error deleting delay event:", e);
        }
    }
}


// ---- 6. Report Filtering Visibility Toggles ----
function onReportPeriodTypeChange() {
    const periodType = document.getElementById('report-period-type').value;
    
    document.getElementById('report-date-container').style.display = 'none';
    document.getElementById('report-month-container').style.display = 'none';
    document.getElementById('report-year-container').style.display = 'none';
    document.getElementById('report-range-container').style.display = 'none';

    if (periodType === 'DAILY') {
        document.getElementById('report-date-container').style.display = 'block';
    } else if (periodType === 'MONTHLY') {
        document.getElementById('report-month-container').style.display = 'block';
    } else if (periodType === 'YEARLY') {
        document.getElementById('report-year-container').style.display = 'block';
    } else if (periodType === 'CUSTOM') {
        document.getElementById('report-range-container').style.display = 'flex';
    }
    
    generateDPRReport();
}

function onReportFilterChange() {
    generateDPRReport();
}


// ---- 7. Daily Production Report (DPR) Dynamic Logic ----
async function generateDPRReport() {
    const shopId = document.getElementById('report-shop-filter').value;
    const areaId = document.getElementById('report-area-filter').value;
    const periodType = document.getElementById('report-period-type').value;

    let url = `/api/report?shopId=${shopId}`;
    if (areaId !== 'All') url += `&areaId=${areaId}`;
    url += `&reportType=${periodType}`;

    const dateVal = document.getElementById('report-date-input').value;
    const monthVal = document.getElementById('report-month-input').value;
    const yearVal = document.getElementById('report-year-input').value;
    const fromDate = document.getElementById('report-from-date').value;
    const toDate = document.getElementById('report-to-date').value;
    const typeFilter = document.getElementById('report-type-filter').value;
    const shiftFilter = document.getElementById('report-shift-filter').value;

    if (dateVal) url += `&date=${dateVal}`;
    if (monthVal) url += `&month=${monthVal}`;
    if (yearVal) url += `&year=${yearVal}`;
    if (fromDate) url += `&fromDate=${fromDate}`;
    if (toDate) url += `&toDate=${toDate}`;
    if (typeFilter !== 'All') url += `&delayTypeId=${typeFilter}`;
    if (shiftFilter !== 'All') url += `&shift=${shiftFilter}`;

    // Manage UI headings
    let periodText = "";
    if (periodType === 'DAILY') periodText = `Date: ${dateVal || 'Today'}`;
    else if (periodType === 'MONTHLY') periodText = `Month: ${monthVal || 'Current'}`;
    else if (periodType === 'YEARLY') periodText = `Year: FY ${yearVal || 'Current'}`;
    else if (periodType === 'CUSTOM') periodText = `Range: ${fromDate || ''} to ${toDate || ''}`;

    const shopText = document.getElementById('report-shop-filter').options[document.getElementById('report-shop-filter').selectedIndex].text;
    document.getElementById('report-date-heading').innerHTML = `
        <strong>Shop:</strong> ${shopText} • 
        <strong>Reporting Frame:</strong> ${periodText}
    `;

    try {
        const res = await fetch(url);
        const apiData = await res.json();
        if (!apiData.success || !apiData.data) {
            console.error("Failed to generate report:", apiData.message);
            return;
        }

        const report = apiData.data;

        // Render department-specific report
        if (report.reportType === 'PLATE_MILL') {
            renderPlateMillReport(report);
        } else if (report.reportType === 'SMS') {
            renderSMSReport(report);
        }

        // Load charts dynamically
        renderCharts(report.charts);

    } catch (e) {
        console.error("Error generating DPR Report:", e);
    }
}

function getVal(areaReport, dayOrMtd, typeCode) {
    if (!areaReport) return '00:00';
    const metrics = dayOrMtd === 'day' ? areaReport.day : areaReport.mtd;
    if (!metrics || !metrics.delayTypeDurations) return '00:00';
    return metrics.delayTypeDurations[typeCode] || '00:00';
}

function getValDirect(areaReport, dayOrMtd, key) {
    if (!areaReport) return '00:00';
    const metrics = dayOrMtd === 'day' ? areaReport.day : areaReport.mtd;
    if (!metrics) return '00:00';
    return metrics[key] || '00:00';
}

function getParamVal(areaReport, dayOrMtd, key, formatFn) {
    if (!areaReport) return '0.00';
    const metrics = dayOrMtd === 'day' ? areaReport.day : areaReport.mtd;
    if (!metrics) return '0.00';
    const val = metrics[key];
    if (val === undefined || val === null) return '0.00';
    if (formatFn) return formatFn(val);
    return val;
}

function getMonthLabel() {
    const periodType = document.getElementById('report-period-type').value;
    if (periodType === 'DAILY') return "Month (MTD)";
    else if (periodType === 'MONTHLY') return "Month";
    else if (periodType === 'YEARLY') return "Year";
    else if (periodType === 'CUSTOM') return "Range";
    return "Month";
}

function renderPlateMillReport(report) {
    const showDay = (document.getElementById('report-period-type').value === 'DAILY');
    const areaFilter = document.getElementById('report-area-filter').value;
    const monthLabel = getMonthLabel();

    const mill = report.areaReports.MILL || null;
    const norm = report.areaReports.NORM || null;

    const visibleCols = showDay ? 2 : 1;
    const showMill = (areaFilter === 'All' || areaFilter === '1');
    const showNorm = (areaFilter === 'All' || areaFilter === '2');

    let tableHtml = `
        <div class="card-header">
            <div>
                <h2 class="card-title">${report.shopName} Production Division - Daily Production Report (DPR)</h2>
                <span class="card-subtitle" id="report-date-heading-val">Reporting Frame: ${document.getElementById('report-date-heading').textContent}</span>
            </div>
            <div class="btn-group" style="display: flex; gap: 0.5rem;">
                <button class="btn btn-secondary" onclick="exportDPRReportExcel()">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                    Export Excel (.xlsx)
                </button>
                <button class="btn btn-primary" onclick="exportDelayLogPdf()" style="background-color: var(--accent); border-color: var(--accent); display: inline-flex; align-items: center; gap: 0.25rem;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                    Export Delay Logs (PDF)
                </button>
            </div>
        </div>
        <div class="card-body" style="padding: 0;">
            <div class="table-responsive">
                <table class="premium-table dpr-table">
                    <thead>
                        <tr>
                            <th rowspan="2" style="vertical-align: middle; width: 280px; border-right: 1.5px solid var(--border-color);">Delay Type / Parameter</th>
                            ${showMill ? `<th colspan="${visibleCols}" class="text-center mill-col-group" style="border-right: 1.5px solid var(--border-color);">Mill Section</th>` : ''}
                            ${showNorm ? `<th colspan="${visibleCols}" class="text-center norm-col-group">Normalizing Furnace</th>` : ''}
                        </tr>
                        <tr>
                            ${showMill && showDay ? `<th class="text-right mill-col-group" style="width: 180px;">Mill Day (HH:MM)</th>` : ''}
                            ${showMill ? `<th class="text-right mill-col-group" style="width: 180px; ${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${showDay ? 'Mill' : 'Mill Section'} ${monthLabel} (HH:MM)</th>` : ''}
                            ${showNorm && showDay ? `<th class="text-right norm-col-group" style="width: 180px;">Norm Day (HH:MM)</th>` : ''}
                            ${showNorm ? `<th class="text-right norm-col-group" style="width: 180px;">${showDay ? 'Norm' : 'Normalizing Furnace'} ${monthLabel} (HH:MM)</th>` : ''}
                        </tr>
                    </thead>
                    <tbody>
    `;

    const rowTypes = [
        { label: "Planned", key: "PLANNED" },
        { label: "Electrical", key: "ELEC" },
        { label: "Mechanical", key: "MECH" },
        { label: "Operation", key: "OPER" },
        { label: "SBS", key: "SBS" },
        { label: "Fuel/EMD", key: "FUEL" },
        { label: "Power", key: "POWER" },
        { label: "MSDS", key: "MSDS" },
        { label: "Others", key: "OTHERS" }
    ];

    rowTypes.forEach((row, i) => {
        const isLastType = (i === rowTypes.length - 1);
        const borderStyle = isLastType ? 'style="border-bottom: 2px solid var(--primary-light);"' : '';
        tableHtml += `
            <tr ${borderStyle}>
                <td style="font-weight: 600; border-right: 1.5px solid var(--border-color);">${row.label}</td>
                ${showMill && showDay ? `<td class="text-right mill-col-group">${getVal(mill, 'day', row.key)}</td>` : ''}
                ${showMill ? `<td class="text-right mill-col-group" style="${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getVal(mill, 'mtd', row.key)}</td>` : ''}
                ${showNorm && showDay ? `<td class="text-right norm-col-group">${getVal(norm, 'day', row.key)}</td>` : ''}
                ${showNorm ? `<td class="text-right norm-col-group">${getVal(norm, 'mtd', row.key)}</td>` : ''}
            </tr>
        `;
    });

    tableHtml += `
        <tr class="total-row">
            <td style="border-right: 1.5px solid var(--border-color);">Total</td>
            ${showMill && showDay ? `<td class="text-right mill-col-group">${getValDirect(mill, 'day', 'totalDelay')}</td>` : ''}
            ${showMill ? `<td class="text-right mill-col-group" style="${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(mill, 'mtd', 'totalDelay')}</td>` : ''}
            ${showNorm && showDay ? `<td class="text-right norm-col-group">${getValDirect(norm, 'day', 'totalDelay')}</td>` : ''}
            ${showNorm ? `<td class="text-right norm-col-group">${getValDirect(norm, 'mtd', 'totalDelay')}</td>` : ''}
        </tr>
        <tr class="subtotal-row">
            <td style="border-right: 1.5px solid var(--border-color);">Controllable</td>
            ${showMill && showDay ? `<td class="text-right mill-col-group">${getValDirect(mill, 'day', 'controllable')}</td>` : ''}
            ${showMill ? `<td class="text-right mill-col-group" style="${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(mill, 'mtd', 'controllable')}</td>` : ''}
            ${showNorm && showDay ? `<td class="text-right norm-col-group">${getValDirect(norm, 'day', 'controllable')}</td>` : ''}
            ${showNorm ? `<td class="text-right norm-col-group">${getValDirect(norm, 'mtd', 'controllable')}</td>` : ''}
        </tr>
        <tr class="subtotal-row" style="border-bottom: 2px solid var(--primary-light);">
            <td style="border-right: 1.5px solid var(--border-color);">Non-Controllable</td>
            ${showMill && showDay ? `<td class="text-right mill-col-group">${getValDirect(mill, 'day', 'nonControllable')}</td>` : ''}
            ${showMill ? `<td class="text-right mill-col-group" style="${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(mill, 'mtd', 'nonControllable')}</td>` : ''}
            ${showNorm && showDay ? `<td class="text-right norm-col-group">${getValDirect(norm, 'day', 'nonControllable')}</td>` : ''}
            ${showNorm ? `<td class="text-right norm-col-group">${getValDirect(norm, 'mtd', 'nonControllable')}</td>` : ''}
        </tr>
    `;

    const params = [
        { label: "Available Hours", field: "availableHours" },
        { label: "Hot Hours", field: "hotHours" },
        { label: "Production/HR (T/H)", field: "productionPerHour", format: (v) => v.toFixed(1) },
        { label: "Utilization %", field: "utilizationPct", format: (v) => v.toFixed(1) + "%" },
        { label: "Availability %", field: "availabilityPct", format: (v) => v.toFixed(1) + "%" },
        { label: "Avg Hot Hours (H/Day)", field: "avgHotHours", isMtdOnly: true },
        { label: "Working Days", field: "workingDays", format: (v) => v.toFixed(2) },
        { label: "No Of Work Shift", field: "shifts", format: (v) => v.toFixed(2) }
    ];

    params.forEach(p => {
        tableHtml += `
            <tr>
                <td style="font-weight: 600; border-right: 1.5px solid var(--border-color);">${p.label}</td>
                ${showMill && showDay ? `<td class="text-right mill-col-group">${p.isMtdOnly ? '—' : getParamVal(mill, 'day', p.field, p.format)}</td>` : ''}
                ${showMill ? `<td class="text-right mill-col-group" style="${showNorm ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getParamVal(mill, 'mtd', p.field, p.format)}</td>` : ''}
                ${showNorm && showDay ? `<td class="text-right norm-col-group">${p.isMtdOnly ? '—' : getParamVal(norm, 'day', p.field, p.format)}</td>` : ''}
                ${showNorm ? `<td class="text-right norm-col-group">${getParamVal(norm, 'mtd', p.field, p.format)}</td>` : ''}
            </tr>
        `;
    });

    tableHtml += `
                    </tbody>
                </table>
            </div>
        </div>
    `;

    document.getElementById('dpr-report-card').innerHTML = tableHtml;
    updateKPICards('Mill Section', mill ? mill.mtd : null, 'Normalizing Furnace', norm ? norm.mtd : null);
}

function renderSMSReport(report) {
    const showDay = (document.getElementById('report-period-type').value === 'DAILY');
    const areaFilter = document.getElementById('report-area-filter').value;
    const monthLabel = getMonthLabel();

    const sms2 = report.areaReports.SMS_2 || null;
    const sms3 = report.areaReports.SMS_3 || null;

    const visibleCols = showDay ? 2 : 1;
    const showSMS2 = (areaFilter === 'All' || areaFilter === '3');
    const showSMS3 = (areaFilter === 'All' || areaFilter === '4');

    let tableHtml = `
        <div class="card-header">
            <div>
                <h2 class="card-title">${report.shopName} Production Division - Daily Production Report (DPR)</h2>
                <span class="card-subtitle" id="report-date-heading-val">Reporting Frame: ${document.getElementById('report-date-heading').textContent}</span>
            </div>
            <div class="btn-group" style="display: flex; gap: 0.5rem;">
                <button class="btn btn-secondary" onclick="exportDPRReportExcel()">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                    Export Excel (.xlsx)
                </button>
                <button class="btn btn-primary" onclick="exportDelayLogPdf()" style="background-color: var(--accent); border-color: var(--accent); display: inline-flex; align-items: center; gap: 0.25rem;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                    Export Delay Logs (PDF)
                </button>
            </div>
        </div>
        <div class="card-body" style="padding: 0;">
            <div class="table-responsive">
                <table class="premium-table dpr-table">
                    <thead>
                        <tr>
                            <th rowspan="2" style="vertical-align: middle; width: 280px; border-right: 1.5px solid var(--border-color);">Delay Type / Parameter</th>
                            ${showSMS2 ? `<th colspan="${visibleCols}" class="text-center sms-col-group" style="border-right: 1.5px solid var(--border-color);">SMS-2 Section</th>` : ''}
                            ${showSMS3 ? `<th colspan="${visibleCols}" class="text-center sms-col-group">SMS-3 Section</th>` : ''}
                        </tr>
                        <tr>
                            ${showSMS2 && showDay ? `<th class="text-right sms-col-group" style="width: 180px;">SMS-2 Day (HH:MM)</th>` : ''}
                            ${showSMS2 ? `<th class="text-right sms-col-group" style="width: 180px; ${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${showDay ? 'SMS-2' : 'SMS-2 Section'} ${monthLabel} (HH:MM)</th>` : ''}
                            ${showSMS3 && showDay ? `<th class="text-right sms-col-group" style="width: 180px;">SMS-3 Day (HH:MM)</th>` : ''}
                            ${showSMS3 ? `<th class="text-right sms-col-group" style="width: 180px;">${showDay ? 'SMS-3' : 'SMS-3 Section'} ${monthLabel} (HH:MM)</th>` : ''}
                        </tr>
                    </thead>
                    <tbody>
    `;

    const rowTypes = [
        { label: "Planned", key: "PLANNED" },
        { label: "Electrical", key: "ELEC" },
        { label: "Mechanical", key: "MECH" },
        { label: "Operation", key: "OPER" },
        { label: "SBS", key: "SBS" },
        { label: "Fuel/EMD", key: "FUEL" },
        { label: "Power", key: "POWER" },
        { label: "MSDS", key: "MSDS" },
        { label: "Gas/Power Shortage", key: "GAS_POWER_SHORTAGE" },
        { label: "RM Shortage", key: "RM_SHORTAGE" },
        { label: "Others", key: "OTHERS" }
    ];

    rowTypes.forEach((row, i) => {
        const isLastType = (i === rowTypes.length - 1);
        const borderStyle = isLastType ? 'style="border-bottom: 2px solid var(--primary-light);"' : '';
        tableHtml += `
            <tr ${borderStyle}>
                <td style="font-weight: 600; border-right: 1.5px solid var(--border-color);">${row.label}</td>
                ${showSMS2 && showDay ? `<td class="text-right sms-col-group">${getVal(sms2, 'day', row.key)}</td>` : ''}
                ${showSMS2 ? `<td class="text-right sms-col-group" style="${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getVal(sms2, 'mtd', row.key)}</td>` : ''}
                ${showSMS3 && showDay ? `<td class="text-right sms-col-group">${getVal(sms3, 'day', row.key)}</td>` : ''}
                ${showSMS3 ? `<td class="text-right sms-col-group">${getVal(sms3, 'mtd', row.key)}</td>` : ''}
            </tr>
        `;
    });

    tableHtml += `
        <tr class="total-row">
            <td style="border-right: 1.5px solid var(--border-color);">Total</td>
            ${showSMS2 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms2, 'day', 'totalDelay')}</td>` : ''}
            ${showSMS2 ? `<td class="text-right sms-col-group" style="${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(sms2, 'mtd', 'totalDelay')}</td>` : ''}
            ${showSMS3 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'day', 'totalDelay')}</td>` : ''}
            ${showSMS3 ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'mtd', 'totalDelay')}</td>` : ''}
        </tr>
        <tr class="subtotal-row">
            <td style="border-right: 1.5px solid var(--border-color);">Controllable</td>
            ${showSMS2 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms2, 'day', 'controllable')}</td>` : ''}
            ${showSMS2 ? `<td class="text-right sms-col-group" style="${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(sms2, 'mtd', 'controllable')}</td>` : ''}
            ${showSMS3 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'day', 'controllable')}</td>` : ''}
            ${showSMS3 ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'mtd', 'controllable')}</td>` : ''}
        </tr>
        <tr class="subtotal-row" style="border-bottom: 2px solid var(--primary-light);">
            <td style="border-right: 1.5px solid var(--border-color);">Non-Controllable</td>
            ${showSMS2 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms2, 'day', 'nonControllable')}</td>` : ''}
            ${showSMS2 ? `<td class="text-right sms-col-group" style="${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getValDirect(sms2, 'mtd', 'nonControllable')}</td>` : ''}
            ${showSMS3 && showDay ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'day', 'nonControllable')}</td>` : ''}
            ${showSMS3 ? `<td class="text-right sms-col-group">${getValDirect(sms3, 'mtd', 'nonControllable')}</td>` : ''}
        </tr>
    `;

    const params = [
        { label: "Available Hours", field: "availableHours" },
        { label: "Hot Hours", field: "hotHours" },
        { label: "Production/HR (T/H)", field: "productionPerHour", format: (v) => v.toFixed(1) },
        { label: "Utilization %", field: "utilizationPct", format: (v) => v.toFixed(1) + "%" },
        { label: "Availability %", field: "availabilityPct", format: (v) => v.toFixed(1) + "%" },
        { label: "Avg Hot Hours (H/Day)", field: "avgHotHours", isMtdOnly: true },
        { label: "Working Days", field: "workingDays", format: (v) => v.toFixed(2) },
        { label: "No Of Work Shift", field: "shifts", format: (v) => v.toFixed(2) }
    ];

    params.forEach(p => {
        tableHtml += `
            <tr>
                <td style="font-weight: 600; border-right: 1.5px solid var(--border-color);">${p.label}</td>
                ${showSMS2 && showDay ? `<td class="text-right sms-col-group">${p.isMtdOnly ? '—' : getParamVal(sms2, 'day', p.field, p.format)}</td>` : ''}
                ${showSMS2 ? `<td class="text-right sms-col-group" style="${showSMS3 ? 'border-right: 1.5px solid var(--border-color);' : ''}">${getParamVal(sms2, 'mtd', p.field, p.format)}</td>` : ''}
                ${showSMS3 && showDay ? `<td class="text-right sms-col-group">${p.isMtdOnly ? '—' : getParamVal(sms3, 'day', p.field, p.format)}</td>` : ''}
                ${showSMS3 ? `<td class="text-right sms-col-group">${getParamVal(sms3, 'mtd', p.field, p.format)}</td>` : ''}
            </tr>
        `;
    });

    tableHtml += `
                    </tbody>
                </table>
            </div>
        </div>
    `;

    document.getElementById('dpr-report-card').innerHTML = tableHtml;
    updateKPICards('SMS-2', sms2 ? sms2.mtd : null, 'SMS-3', sms3 ? sms3.mtd : null);
}

function updateKPICards(area1Name, area1Metrics, area2Name, area2Metrics) {
    document.getElementById('kpi-rep-title-1').textContent = `${area1Name} Availability (MTD)`;
    document.getElementById('kpi-rep-title-2').textContent = `${area1Name} Utilization (MTD)`;
    document.getElementById('kpi-rep-title-3').textContent = `${area2Name} Availability (MTD)`;
    document.getElementById('kpi-rep-title-4').textContent = `${area2Name} Utilization (MTD)`;

    const a1Avail = area1Metrics ? area1Metrics.availabilityPct : 0;
    const a1Util = area1Metrics ? area1Metrics.utilizationPct : 0;
    const a2Avail = area2Metrics ? area2Metrics.availabilityPct : 0;
    const a2Util = area2Metrics ? area2Metrics.utilizationPct : 0;

    const val1 = document.getElementById('kpi-rep-mill-avail');
    val1.textContent = `${a1Avail.toFixed(1)}%`;
    toggleKPIClass(val1.closest('.kpi-card'), a1Avail >= 80.0);

    const val2 = document.getElementById('kpi-rep-mill-util');
    val2.textContent = `${a1Util.toFixed(1)}%`;
    toggleKPIClass(val2.closest('.kpi-card'), a1Util >= 92.0);

    const val3 = document.getElementById('kpi-rep-norm-avail');
    val3.textContent = `${a2Avail.toFixed(1)}%`;
    toggleKPIClass(val3.closest('.kpi-card'), a2Avail >= 80.0);

    const val4 = document.getElementById('kpi-rep-norm-util');
    val4.textContent = `${a2Util.toFixed(1)}%`;
    toggleKPIClass(val4.closest('.kpi-card'), a2Util >= 92.0);
}

function toggleKPIClass(element, isTargetMet) {
    if (isTargetMet) {
        element.classList.remove('kpi-alert');
        element.style.borderColor = 'var(--border-color)';
    } else {
        element.classList.add('kpi-alert');
        element.style.borderColor = 'var(--danger)';
    }
}


// ---- 8. CSV and Excel Backend Exports ----
function getExportParamsUrl() {
    const shopId = document.getElementById('report-shop-filter').value;
    const areaId = document.getElementById('report-area-filter').value;
    const periodType = document.getElementById('report-period-type').value;

    let params = `shopId=${shopId}`;
    if (areaId !== 'All') params += `&areaId=${areaId}`;
    params += `&reportType=${periodType}`;

    const dateVal = document.getElementById('report-date-input').value;
    const monthVal = document.getElementById('report-month-input').value;
    const yearVal = document.getElementById('report-year-input').value;
    const fromDate = document.getElementById('report-from-date').value;
    const toDate = document.getElementById('report-to-date').value;
    const typeFilter = document.getElementById('report-type-filter').value;
    const shiftFilter = document.getElementById('report-shift-filter').value;

    if (dateVal) params += `&date=${dateVal}`;
    if (monthVal) params += `&month=${monthVal}`;
    if (yearVal) params += `&year=${yearVal}`;
    if (fromDate) params += `&fromDate=${fromDate}`;
    if (toDate) params += `&toDate=${toDate}`;
    if (typeFilter !== 'All') params += `&delayTypeId=${typeFilter}`;
    if (shiftFilter !== 'All') params += `&shift=${shiftFilter}`;

    return params;
}


function exportDPRReportExcel() {
    const params = getExportParamsUrl();
    window.location.href = `/api/report/export-excel?${params}`;
}

function exportDelayLogPdf() {
    const params = getExportParamsUrl();
    window.location.href = `/api/report/export-pdf?${params}`;
}



// ---- 9. Dashboard Charts Rendering (Chart.js) ----
function renderCharts(chartsData) {
    if (!chartsData) return;

    // A. Daily Trend
    if (chartDailyTrend) chartDailyTrend.destroy();
    const ctxDaily = document.getElementById('chart-daily-trend').getContext('2d');
    chartDailyTrend = new Chart(ctxDaily, {
        type: 'line',
        data: {
            labels: chartsData.dailyTrendLabels,
            datasets: [{
                label: 'Delay Hours',
                data: chartsData.dailyTrendValues,
                borderColor: '#1e88e5',
                backgroundColor: 'rgba(30, 136, 229, 0.1)',
                borderWidth: 2,
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(255, 255, 255, 0.08)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // B. Delay Type Distribution
    if (chartTypeDistribution) chartTypeDistribution.destroy();
    const ctxType = document.getElementById('chart-type-distribution').getContext('2d');
    chartTypeDistribution = new Chart(ctxType, {
        type: 'doughnut',
        data: {
            labels: chartsData.distributionLabels,
            datasets: [{
                data: chartsData.distributionValues,
                backgroundColor: ['#ffb74d', '#4db6ac', '#90caf9', '#ba68c8', '#e57373', '#a1887f', '#90a4ae', '#afb42b', '#64b5f6'],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'right', labels: { boxWidth: 12, padding: 8 } } }
        }
    });

    // C. Monthly Trend
    if (chartMonthlyTrend) chartMonthlyTrend.destroy();
    const ctxMonth = document.getElementById('chart-monthly-trend').getContext('2d');
    chartMonthlyTrend = new Chart(ctxMonth, {
        type: 'bar',
        data: {
            labels: chartsData.monthlyTrendLabels,
            datasets: [{
                label: 'Delay Hours',
                data: chartsData.monthlyTrendValues,
                backgroundColor: '#26a69a',
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(255, 255, 255, 0.08)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // D. Top Delays
    if (chartTopDelays) chartTopDelays.destroy();
    const ctxTop = document.getElementById('chart-top-delays').getContext('2d');
    chartTopDelays = new Chart(ctxTop, {
        type: 'bar',
        data: {
            labels: chartsData.topDelaysLabels,
            datasets: [{
                label: 'Aggregate Hours',
                data: chartsData.topDelaysValues,
                backgroundColor: '#ef5350',
                borderRadius: 4
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { beginAtZero: true, grid: { color: 'rgba(255, 255, 255, 0.08)' } },
                y: { grid: { display: false } }
            }
        }
    });
}


// ---- 10. Dedicated Master Management CRUD Tab ----


// ---- 11. Core Application Screen Manager ----
function switchScreen(screenName) {
    document.querySelectorAll('.screen-panel').forEach(panel => panel.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    
    if (screenName === 'entry') {
        document.getElementById('screen-entry').classList.add('active');
        document.getElementById('btn-entry').classList.add('active');
        renderEnteredRecordsTable();
    } else if (screenName === 'report') {
        document.getElementById('screen-report').classList.add('active');
        document.getElementById('btn-report').classList.add('active');
        generateDPRReport();
    }
}


// ---- 12. Operational Inputs Panel Management ----
async function loadDayMetrics() {
    const dateVal = document.getElementById('metrics-date').value;
    if (!dateVal) return;

    const configBoxes = document.querySelectorAll('#operational-inputs-container .config-box');
    for (const box of configBoxes) {
        const areaId = box.dataset.areaId;
        try {
            const res = await fetch(`/api/operational-input?areaId=${areaId}&date=${dateVal}`);
            const data = await res.json();
            if (data.success && data.data) {
                box.querySelector('.metric-avail').value = data.data.availableHours;
                box.querySelector('.metric-prod').value = data.data.productionTonnage;
            } else {
                // reset to default fallbacks
                box.querySelector('.metric-avail').value = "24.0";
                const areaCode = box.dataset.areaCode;
                let defaultProd = 1000;
                if (areaCode === 'MILL') defaultProd = 4000;
                else if (areaCode === 'NORM') defaultProd = 600;
                else if (areaCode === 'SMS_2') defaultProd = 3000;
                else if (areaCode === 'SMS_3') defaultProd = 3500;
                box.querySelector('.metric-prod').value = defaultProd;
            }
        } catch (e) {
            console.error("Error loading operational input:", e);
        }
    }
}

async function saveDayMetrics() {
    const dateVal = document.getElementById('metrics-date').value;
    if (!dateVal) {
        alert("Please select a date first.");
        return;
    }

    const configBoxes = document.querySelectorAll('#operational-inputs-container .config-box');
    let hasError = false;
    let savedCount = 0;

    for (const box of configBoxes) {
        const areaId = box.dataset.areaId;
        const avail = parseFloat(box.querySelector('.metric-avail').value) || 0;
        const prod = parseFloat(box.querySelector('.metric-prod').value) || 0;

        if (avail < 0 || avail > 24) {
            alert("Available hours must be between 0 and 24 hours.");
            hasError = true;
            break;
        }

        try {
            const res = await fetch('/api/operational-input', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    areaId: parseInt(areaId),
                    logDate: dateVal,
                    availableHours: avail,
                    productionTonnage: prod
                })
            });
            const data = await res.json();
            if (data.success) {
                savedCount++;
            }
        } catch (e) {
            console.error("Error saving operational inputs:", e);
        }
    }

    if (!hasError) {
        if (savedCount === configBoxes.length) {
            alert(`Operational inputs updated for ${dateVal}.`);
            if (document.getElementById('screen-report').classList.contains('active')) {
                generateDPRReport();
            }
        } else {
            alert("Some operational inputs failed to save.");
        }
    }
}

function resetDatabaseToSeeds() {
    alert("Database seeds are synchronized on application startup.");
}


// ---- 13. Application Initialisation Routine ----
async function initApp() {
    const defaultDate = "2026-06-21";
    document.getElementById('entry-date').value = defaultDate;
    document.getElementById('metrics-date').value = defaultDate;

    // Report filter default dates
    document.getElementById('report-date-input').value = defaultDate;
    document.getElementById('report-month-input').value = "2026-06";
    document.getElementById('report-from-date').value = "2026-06-01";
    document.getElementById('report-to-date').value = "2026-06-30";

    // Setup report years dynamically
    const yearSelect = document.getElementById('report-year-input');
    const curYear = new Date().getFullYear();
    yearSelect.innerHTML = `
        <option value="${curYear - 1}">${curYear - 1}-${curYear}</option>
        <option value="${curYear}" selected>${curYear}-${curYear + 1}</option>
        <option value="${curYear + 1}">${curYear + 1}-${curYear + 2}</option>
    `;

    // Fetch initial list reference tables
    await fetchMetadata();
    
    // Bind event handlers
    document.getElementById('entry-date').addEventListener('change', (e) => {
        document.getElementById('metrics-date').value = e.target.value;
        loadDayMetrics();
        renderEnteredRecordsTable();
        if (document.getElementById('screen-report').classList.contains('active')) {
            generateDPRReport();
        }
    });

    initSearchableDropdowns();

    // Bind custom dropdown wrap changes
    document.getElementById('entry-shop-dropdown').addEventListener('change', (e) => {
        onEntryShopChange(e.detail.value);
    });
    document.getElementById('entry-type-dropdown').addEventListener('change', (e) => {
        onEntryTypeChange(e.detail.value);
    });

    await loadDayMetrics();
    await renderEnteredRecordsTable();
    
    // Force default searchable dropdown states
    setCustomDropdownValue('entry-shop-dropdown', '1');
    await onEntryShopChange('1');
}

window.addEventListener('DOMContentLoaded', () => {
    initApp();
});
