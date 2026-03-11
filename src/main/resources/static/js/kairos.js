function toggleDarkMode() {
    const html = document.documentElement;
    const current = html.getAttribute('data-bs-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-bs-theme', next);
    localStorage.setItem('theme', next);
}

document.addEventListener('DOMContentLoaded', function() {
    const saved = localStorage.getItem('theme') || 'dark';
    document.documentElement.setAttribute('data-bs-theme', saved);
    initResourceStatusStream();
});

function initResourceStatusStream() {
    const hasLiveResourceView = document.querySelector('.resource-row[data-resource-id], .resource-detail[data-resource-id]');
    if (!hasLiveResourceView || typeof EventSource === 'undefined') {
        return;
    }

    const eventSource = new EventSource('/api/resources/stream');

    eventSource.addEventListener('snapshot', function(event) {
        const updates = parseUpdatePayload(event.data);
        if (!Array.isArray(updates)) {
            return;
        }
        updates.forEach(updateResourceRow);
    });

    eventSource.addEventListener('resource-update', function(event) {
        const update = parseUpdatePayload(event.data);
        if (!update) {
            return;
        }
        updateResourceView(update);
    });

    eventSource.addEventListener('resource-checking', function(event) {
        const checking = parseUpdatePayload(event.data);
        if (!checking || checking.resourceId === undefined || checking.resourceId === null) {
            return;
        }
        setResourceChecking(checking.resourceId, true);
    });

    eventSource.onerror = function() {
        // Browser EventSource handles reconnects automatically.
    };
}

function parseUpdatePayload(raw) {
    try {
        return JSON.parse(raw);
    } catch (error) {
        return null;
    }
}

function updateResourceRow(update) {
    if (!update || update.resourceId === undefined || update.resourceId === null) {
        return;
    }

    const containers = findResourceContainers(update.resourceId);
    if (containers.length === 0) {
        return;
    }

    containers.forEach(function(container) {
        setRowChecking(container, false);
        updateStatusDot(container, update.currentStatus);
        updateTimeline(container, update.timelineBlocks);
        updateUptime(container, update.uptimePercentage);
    });
}

function updateResourceView(update) {
    updateResourceRow(update);
}

function setResourceChecking(resourceId, checking) {
    const containers = findResourceContainers(resourceId);
    if (containers.length === 0) {
        return;
    }

    containers.forEach(function(container) {
        setRowChecking(container, checking);
    });
}

function findResourceContainers(resourceId) {
    const selector = '.resource-row[data-resource-id="' + resourceId + '"]'
        + ', .resource-detail[data-resource-id="' + resourceId + '"]';
    return document.querySelectorAll(selector);
}

function setRowChecking(row, checking) {
    const dot = row.querySelector('[data-role="status-dot"]');
    if (!dot) {
        return;
    }
    dot.classList.toggle('status-checking', checking);
}

function updateStatusDot(row, status) {
    const dot = row.querySelector('[data-role="status-dot"]');
    if (!dot) {
        return;
    }

    dot.classList.remove('status-available', 'status-not-available', 'status-unknown');
    dot.classList.add('status-' + normalizeStatus(status));
}

function updateTimeline(row, timelineBlocks) {
    if (!Array.isArray(timelineBlocks)) {
        return;
    }

    const container = row.querySelector('.timeline-container');
    if (!container) {
        return;
    }

    const fragment = document.createDocumentFragment();
    timelineBlocks.forEach(function(block) {
        const status = normalizeStatus(block);
        const blockElement = document.createElement('span');
        blockElement.className = 'timeline-block ' + status;
        blockElement.title = status;
        fragment.appendChild(blockElement);
    });

    container.replaceChildren(fragment);
}

function updateUptime(row, uptimePercentage) {
    const uptimeElement = row.querySelector('.resource-uptime');
    if (!uptimeElement || typeof uptimePercentage !== 'number') {
        return;
    }

    uptimeElement.textContent = uptimePercentage.toFixed(1) + '%';
}

function normalizeStatus(status) {
    if (status === 'available' || status === 'not-available' || status === 'unknown') {
        return status;
    }
    return 'unknown';
}
