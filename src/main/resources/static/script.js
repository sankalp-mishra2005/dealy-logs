// BSP Delay Management System - JavaScript Logic with REST API Integration

// Global lists populated dynamically from server
let activeShifts = [];
let allShops = [];
let allShopAreas = [];
let allDelayTypes = [];
const activeShopId = 1; // Plate Mill

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
        }

        // 2. Fetch Shop Areas
        const areasRes = await fetch(`/api/shop-areas/${activeShopId}`);
        const areasData = await areasRes.json();
        if (areasData.success) {
            allShopAreas = areasData.data;
            populateAreaDropdowns(allShopAreas);
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
    const startTime = document.getElementById('entry-start-time').value;
    const endTime = document.getElementById('entry-end-time').value;
    const shift = document.getElementById('entry-shift').value;
    const remarks = document.getElementById('entry-remarks').value;
    
    const payload = {
        areaId: parseInt(areaId),
        delayTypeId: parseInt(delayTypeId),
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
    document.getElementById('entry-shift').value = 'A';
    setCustomDropdownValue('entry-area-dropdown', '');
    setCustomDropdownValue('entry-type-dropdown', '');
}

async function renderEnteredRecordsTable() {
    const filterDate = document.getElementById('entry-date').value;
    const tableBody = document.getElementById('entered-records-body');
    tableBody.innerHTML = '';
    
    if (!filterDate) return;

    try {
        const millRes = await fetch(`/api/delay-log?areaId=1&date=${filterDate}`);
        const normRes = await fetch(`/api/delay-log?areaId=2&date=${filterDate}`);
        
        const millData = await millRes.json();
        const normData = await normRes.json();

        let records = [];
        if (millData.success && millData.data) records = records.concat(millData.data);
        if (normData.success && normData.data) records = records.concat(normData.data);

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
            const areaBadge = rec.areaId === 1 ? 'badge-mill' : 'badge-norm';
            const typeClass = `badge-${rec.delayGroup.toLowerCase().replace('/', '')}`;
            
            tr.innerHTML = `
                <td><strong>${rec.logDate}</strong></td>
                <td><span style="font-weight:600; color:var(--primary);">${rec.shift || 'A'}</span></td>
                <td><span class="badge ${areaBadge}">${rec.areaName}</span></td>
                <td><span class="badge badge-delay ${typeClass}">${rec.typeName}</span></td>
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

function editDelayEvent(id) {
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
    
    document.getElementById('entry-start-time').value = record.startTime || '00:00';
    document.getElementById('entry-end-time').value = record.endTime || '00:00';
    document.getElementById('entry-remarks').value = record.remarks;
    
    document.getElementById('entry-shift').value = record.shift || 'A';

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
    
    const areaBadge = record.areaId === 1 ? 'badge-mill' : 'badge-norm';
    const typeClass = `badge-${record.delayGroup.toLowerCase().replace('/', '')}`;

    modalBody.innerHTML = `
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-bottom:1.5rem;">
            <div><strong>Log Date:</strong> ${record.logDate}</div>
            <div><strong>Shift:</strong> ${record.shift || 'A'}</div>
            <div><strong>Shop Area:</strong> <span class="badge ${areaBadge}">${record.areaName}</span></div>
            <div><strong>Delay Type:</strong> <span class="badge badge-delay ${typeClass}">${record.typeName} [${record.delayGroup}]</span></div>
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

    updateReportColumns();

    try {
        const res = await fetch(url);
        const apiData = await res.json();
        if (!apiData.success || !apiData.data) {
            console.error("Failed to generate report:", apiData.message);
            return;
        }

        const report = apiData.data;

        // Render MILL values
        if (report.areaReports.MILL) {
            populateAreaDPRObject('mill', report.areaReports.MILL);
        }
        // Render NORM values
        if (report.areaReports.NORM) {
            populateAreaDPRObject('norm', report.areaReports.NORM);
        }

        // Update KPI Cards
        if (report.areaReports.MILL && report.areaReports.NORM) {
            const millMtd = report.areaReports.MILL.mtd;
            const normMtd = report.areaReports.NORM.mtd;
            updateKPICards(millMtd.availabilityPct, millMtd.utilizationPct, normMtd.availabilityPct, normMtd.utilizationPct);
        }

        // Load charts dynamically
        renderCharts(report.charts);

    } catch (e) {
        console.error("Error generating DPR Report:", e);
    }
}

function populateAreaDPRObject(areaKey, areaReport) {
    const d = areaReport.day;
    const m = areaReport.mtd;

    // Categories
    const categories = ["planned", "electrical", "mechanical", "operation", "sbs", "fuelEmd", "power", "msds", "others"];
    categories.forEach(cat => {
        const elementIdDay = `dpr-${areaKey}-day-${cat.toLowerCase()}`;
        const elementIdMonth = `dpr-${areaKey}-month-${cat.toLowerCase()}`;
        if (document.getElementById(elementIdDay)) {
            document.getElementById(elementIdDay).textContent = d[cat];
        }
        if (document.getElementById(elementIdMonth)) {
            document.getElementById(elementIdMonth).textContent = m[cat];
        }
    });

    // Calculations
    document.getElementById(`dpr-${areaKey}-day-total`).textContent = d.totalDelay;
    document.getElementById(`dpr-${areaKey}-month-total`).textContent = m.totalDelay;

    document.getElementById(`dpr-${areaKey}-day-controllable`).textContent = d.controllable;
    document.getElementById(`dpr-${areaKey}-month-controllable`).textContent = m.controllable;

    document.getElementById(`dpr-${areaKey}-day-noncontrollable`).textContent = d.nonControllable;
    document.getElementById(`dpr-${areaKey}-month-noncontrollable`).textContent = m.nonControllable;

    document.getElementById(`dpr-${areaKey}-day-avail`).textContent = d.availableHours;
    document.getElementById(`dpr-${areaKey}-month-avail`).textContent = m.availableHours;

    document.getElementById(`dpr-${areaKey}-day-hot`).textContent = d.hotHours;
    document.getElementById(`dpr-${areaKey}-month-hot`).textContent = m.hotHours;

    document.getElementById(`dpr-${areaKey}-day-prodhr`).textContent = d.productionPerHour.toFixed(1);
    document.getElementById(`dpr-${areaKey}-month-prodhr`).textContent = m.productionPerHour.toFixed(1);

    document.getElementById(`dpr-${areaKey}-day-util`).textContent = d.utilizationPct.toFixed(1) + "%";
    document.getElementById(`dpr-${areaKey}-month-util`).textContent = m.utilizationPct.toFixed(1) + "%";

    document.getElementById(`dpr-${areaKey}-day-availability`).textContent = d.availabilityPct.toFixed(1) + "%";
    document.getElementById(`dpr-${areaKey}-month-availability`).textContent = m.availabilityPct.toFixed(1) + "%";



    document.getElementById(`dpr-${areaKey}-day-working`).textContent = d.workingDays.toFixed(2);
    document.getElementById(`dpr-${areaKey}-month-working`).textContent = m.workingDays.toFixed(2);

    document.getElementById(`dpr-${areaKey}-day-shifts`).textContent = d.shifts.toFixed(2);
    document.getElementById('dpr-' + areaKey + '-month-shifts').textContent = m.shifts.toFixed(2);
    
    document.getElementById(`dpr-${areaKey}-day-avghot`).textContent = "—";
    document.getElementById(`dpr-${areaKey}-month-avghot`).textContent = m.avgHotHours || "00:00";
}

function updateReportColumns() {
    const areaFilter = document.getElementById('report-area-filter').value;
    const periodType = document.getElementById('report-period-type').value;

    const millCols = document.querySelectorAll('.mill-col-group');
    const normCols = document.querySelectorAll('.norm-col-group');
    
    // Reset all to visible first
    millCols.forEach(el => el.style.display = '');
    normCols.forEach(el => el.style.display = '');

    // 1. Area filtering
    if (areaFilter === '1') {
        normCols.forEach(el => el.style.display = 'none');
    } else if (areaFilter === '2') {
        millCols.forEach(el => el.style.display = 'none');
    }

    // 2. Period filtering
    let showDay = (periodType === 'DAILY');
    let monthLabel = "Month";
    if (periodType === 'DAILY') monthLabel = "Month (MTD)";
    else if (periodType === 'MONTHLY') monthLabel = "Month";
    else if (periodType === 'YEARLY') monthLabel = "Year";
    else if (periodType === 'CUSTOM') monthLabel = "Range";

    const monthHeaders = [document.getElementById('th-mill-month'), document.getElementById('th-norm-month')];
    if (monthHeaders[0]) monthHeaders[0].textContent = `Mill ${monthLabel} (HH:MM)`;
    if (monthHeaders[1]) monthHeaders[1].textContent = `Norm ${monthLabel} (HH:MM)`;

    const dayCells = document.querySelectorAll('[id^="dpr-mill-day"], [id^="dpr-norm-day"], #th-mill-day, #th-norm-day');
    if (!showDay) {
        dayCells.forEach(el => el.style.display = 'none');
    }

    // 3. Update colspans for top headers
    const visibleColsPerArea = showDay ? 2 : 1;
    
    const millSectionTh = document.getElementById('th-mill-section');
    const normSectionTh = document.getElementById('th-norm-section');
    
    if (millSectionTh) millSectionTh.setAttribute('colspan', visibleColsPerArea.toString());
    if (normSectionTh) normSectionTh.setAttribute('colspan', visibleColsPerArea.toString());
}

function updateKPICards(mAvail, mUtil, nAvail, nUtil) {
    const millAvailVal = document.getElementById('kpi-rep-mill-avail');
    millAvailVal.textContent = `${mAvail.toFixed(1)}%`;
    toggleKPIClass(millAvailVal.closest('.kpi-card'), mAvail >= 80.0);
    
    const millUtilVal = document.getElementById('kpi-rep-mill-util');
    millUtilVal.textContent = `${mUtil.toFixed(1)}%`;
    toggleKPIClass(millUtilVal.closest('.kpi-card'), mUtil >= 92.0);
    
    const normAvailVal = document.getElementById('kpi-rep-norm-avail');
    normAvailVal.textContent = `${nAvail.toFixed(1)}%`;
    toggleKPIClass(normAvailVal.closest('.kpi-card'), nAvail >= 80.0);
    
    const normUtilVal = document.getElementById('kpi-rep-norm-util');
    normUtilVal.textContent = `${nUtil.toFixed(1)}%`;
    toggleKPIClass(normUtilVal.closest('.kpi-card'), nUtil >= 92.0);
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

    try {
        const millRes = await fetch(`/api/operational-input?areaId=1&date=${dateVal}`);
        const normRes = await fetch(`/api/operational-input?areaId=2&date=${dateVal}`);
        
        const millData = await millRes.json();
        const normData = await normRes.json();

        if (millData.success && millData.data) {
            const mInput = millData.data;
            document.getElementById('metrics-mill-avail').value = mInput.availableHours;
            document.getElementById('metrics-mill-prod').value = mInput.productionTonnage;
        }

        if (normData.success && normData.data) {
            const nInput = normData.data;
            document.getElementById('metrics-norm-avail').value = nInput.availableHours;
            document.getElementById('metrics-norm-prod').value = nInput.productionTonnage;
        }
    } catch (e) {
        console.error("Error loading daily operational metrics:", e);
    }
}

async function saveDayMetrics() {
    const dateVal = document.getElementById('metrics-date').value;
    if (!dateVal) {
        alert("Please select a date first.");
        return;
    }

    const millAvailVal = parseFloat(document.getElementById('metrics-mill-avail').value) || 0;
    const millProd = parseFloat(document.getElementById('metrics-mill-prod').value) || 0;
    
    const normAvailVal = parseFloat(document.getElementById('metrics-norm-avail').value) || 0;
    const normProd = parseFloat(document.getElementById('metrics-norm-prod').value) || 0;

    if (millAvailVal < 0 || millAvailVal > 24 || normAvailVal < 0 || normAvailVal > 24) {
        alert("Available hours must be between 0 and 24 hours.");
        return;
    }

    try {
        const millRes = await fetch('/api/operational-input', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                areaId: 1,
                logDate: dateVal,
                availableHours: millAvailVal,
                productionTonnage: millProd
            })
        });

        const normRes = await fetch('/api/operational-input', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                areaId: 2,
                logDate: dateVal,
                availableHours: normAvailVal,
                productionTonnage: normProd
            })
        });

        const millData = await millRes.json();
        const normData = await normRes.json();

        if (millData.success && normData.success) {
            alert(`Operational inputs updated for ${dateVal}.`);
            if (document.getElementById('screen-report').classList.contains('active')) {
                generateDPRReport();
            }
        } else {
            alert("Failed to save operational inputs.");
        }
    } catch (e) {
        console.error("Error saving operational inputs:", e);
        alert("Error sending operational data to server.");
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
    await loadDayMetrics();
    await renderEnteredRecordsTable();
    
    // Force default searchable dropdown states
    setCustomDropdownValue('entry-shop-dropdown', '1');
}

window.addEventListener('DOMContentLoaded', () => {
    initApp();
});
