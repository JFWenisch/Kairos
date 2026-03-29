function toggleDarkMode() {
    const html = document.documentElement;
    const current = html.getAttribute('data-bs-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-bs-theme', next);
    localStorage.setItem('theme', next);
}

function formatDateTime(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const mins = String(date.getMinutes()).padStart(2, '0');
    const shortMonth = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][date.getMonth()];
    return shortMonth + ' ' + day + ', ' + year + ' ' + hours + ':' + mins;
}

function calculateStartDateTime(hours) {
    const now = new Date();
    const start = new Date(now.getTime() - hours * 60 * 60 * 1000);
    return start;
}

function initializeTimelineLabels() {
    const startLabels = document.querySelectorAll('[data-role="timeline-range-start"]');
    const endLabels = document.querySelectorAll('.timeline-label-end');
    
    if (startLabels.length === 0 && endLabels.length === 0) {
        return;
    }
    
    const now = new Date();
    const start = calculateStartDateTime(24);
    const startLabel = formatDateTime(start);
    const endLabel = formatDateTime(now);
    
    startLabels.forEach(function(labelElement) {
        labelElement.textContent = startLabel;
        labelElement.setAttribute('title', 'Start: ' + startLabel);
    });
    
    endLabels.forEach(function(labelElement) {
        labelElement.textContent = endLabel;
        labelElement.setAttribute('title', 'End: ' + endLabel);
    });
}

document.addEventListener('DOMContentLoaded', function() {
    const saved = localStorage.getItem('theme') || 'dark';
    document.documentElement.setAttribute('data-bs-theme', saved);
    initializeTimelineLabels();
    initResourceStatusStream();
    refreshAllGroupCounters();
    initAdminResourceSorting();
});

function initResourceStatusStream() {
    const hasLiveResourceView = document.querySelector('.resource-row[data-resource-id], .resource-detail[data-resource-id]');
    if (!hasLiveResourceView) {
        return;
    }

    const rangeControls = document.querySelector('[data-role="timeline-range-controls"]');
    const rangeButtons = rangeControls ? rangeControls.querySelectorAll('[data-timeline-hours]') : [];
    const rangeLabel = document.querySelector('[data-role="timeline-range-label"]');
    const rangeStartLabels = document.querySelectorAll('[data-role="timeline-range-start"]');
    const loadingIndicator = document.querySelector('[data-role="timeline-loading-indicator"]');
    const hasRangeSelector = rangeButtons.length > 0;
    const pollIntervalMs = 10000;
    let pollingStarted = false;
    let currentTimelineHours = 24;

    function applySnapshot(updates) {
        if (!Array.isArray(updates)) {
            return;
        }
        updates.forEach(updateResourceRow);
    }

    function formatRangeLabel(hours) {
        switch (hours) {
            case 168:
                return 'Last 7 days';
            case 720:
                return 'Last 30 days';
            default:
                return 'Last 24 hours';
        }
    }

    function applyRangeLabel(hours) {
        if (!rangeLabel) {
            return;
        }
        rangeLabel.textContent = formatRangeLabel(hours);

        const startDateTime = calculateStartDateTime(hours);
        const startLabel = formatDateTime(startDateTime);
        const endLabel = formatDateTime(new Date());
        
        rangeStartLabels.forEach(function(labelElement) {
            labelElement.textContent = startLabel;
            labelElement.setAttribute('title', 'Start: ' + startLabel);
        });
        
        const endLabels = document.querySelectorAll('.timeline-label-end');
        endLabels.forEach(function(labelElement) {
            labelElement.textContent = endLabel;
            labelElement.setAttribute('title', 'End: ' + endLabel);
        });
    }

    function setTimelineLoading(loading) {
        if (!loadingIndicator) {
            return;
        }

        loadingIndicator.hidden = !loading;
        rangeButtons.forEach(function(button) {
            button.disabled = loading;
        });
    }

    function setSelectedRange(hours) {
        currentTimelineHours = hours;
        rangeButtons.forEach(function(button) {
            const buttonHours = Number(button.getAttribute('data-timeline-hours'));
            const isActive = buttonHours === hours;
            button.classList.toggle('active', isActive);
            button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        });
        applyRangeLabel(hours);
    }

    function buildSnapshotUrl() {
        if (!hasRangeSelector) {
            return '/api/resources/status-updates';
        }
        return '/api/resources/status-updates?hours=' + encodeURIComponent(String(currentTimelineHours));
    }

    function fetchSnapshot(options) {
        const showLoading = options && options.showLoading === true;
        if (showLoading) {
            setTimelineLoading(true);
        }

        return fetch(buildSnapshotUrl(), {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            cache: 'no-store'
        })
            .then(function(response) {
                if (!response.ok) {
                    throw new Error('Polling failed with status ' + response.status);
                }
                return response.json();
            })
            .then(applySnapshot)
            .catch(function() {
                // Retry on next interval.
            })
            .finally(function() {
                if (showLoading) {
                    setTimelineLoading(false);
                }
            });
    }

    function startHttpPolling() {
        if (pollingStarted) {
            return;
        }
        pollingStarted = true;

        const fetchSnapshot = function() {
            fetchSnapshotWithOptionalLoading(false);
        };

        fetchSnapshot();
        window.setInterval(fetchSnapshot, pollIntervalMs);
    }

    function fetchSnapshotWithOptionalLoading(showLoading) {
        fetchSnapshot({ showLoading: showLoading });
    }

    if (hasRangeSelector) {
        const preselected = Array.prototype.find.call(rangeButtons, function(button) {
            return button.classList.contains('active');
        });
        const selectedHours = preselected
            ? Number(preselected.getAttribute('data-timeline-hours'))
            : 24;
        setSelectedRange(selectedHours === 168 || selectedHours === 720 ? selectedHours : 24);
    } else {
        // Initialize timeline labels with default 24h range if no range selector
        const startLabels = document.querySelectorAll('[data-role="timeline-range-start"]');
        const now = new Date();
        const start = calculateStartDateTime(24);
        const startLabel = formatDateTime(start);
        const endLabel = formatDateTime(now);
        
        startLabels.forEach(function(labelElement) {
            labelElement.textContent = startLabel;
            labelElement.setAttribute('title', 'Start: ' + startLabel);
        });
        
        const endLabels = document.querySelectorAll('.timeline-label-end');
        endLabels.forEach(function(labelElement) {
            labelElement.textContent = endLabel;
            labelElement.setAttribute('title', 'End: ' + endLabel);
        });
    }

    rangeButtons.forEach(function(button) {
        button.addEventListener('click', function() {
            const hours = Number(button.getAttribute('data-timeline-hours'));
            if (hours !== 24 && hours !== 168 && hours !== 720) {
                return;
            }
            if (hours === currentTimelineHours) {
                return;
            }
            setSelectedRange(hours);
            fetchSnapshotWithOptionalLoading(true);
        });
    });

    if (typeof EventSource === 'undefined') {
        startHttpPolling();
        return;
    }

    const eventSource = new EventSource('/api/resources/stream');

    eventSource.addEventListener('snapshot', function(event) {
        applySnapshot(parseUpdatePayload(event.data));
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
        eventSource.close();
        startHttpPolling();
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

    refreshAllGroupCounters();
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
        const status = normalizeStatus(resolveTimelineBlockStatus(block));
        const blockElement = document.createElement('span');
        blockElement.className = 'timeline-block ' + status;
        blockElement.title = buildTimelineTooltip(status, resolveTimelineBlockTimestamp(block));
        fragment.appendChild(blockElement);
    });

    container.replaceChildren(fragment);
}

function resolveTimelineBlockStatus(block) {
    if (block && typeof block === 'object') {
        return block.status;
    }
    return block;
}

function resolveTimelineBlockTimestamp(block) {
    if (block && typeof block === 'object') {
        return block.timestamp;
    }
    return null;
}

function buildTimelineTooltip(status, timestamp) {
    const normalizedStatus = normalizeStatus(status);
    const formattedTimestamp = formatTimelineTimestamp(timestamp);
    if (!formattedTimestamp) {
        return normalizedStatus;
    }
    return normalizedStatus + ' · ' + formattedTimestamp;
}

function formatTimelineTimestamp(timestamp) {
    if (typeof timestamp !== 'string' || timestamp.length === 0) {
        return null;
    }

    const parsed = new Date(timestamp);
    if (Number.isNaN(parsed.getTime())) {
        return timestamp;
    }

    return parsed.getFullYear()
        + '-' + String(parsed.getMonth() + 1).padStart(2, '0')
        + '-' + String(parsed.getDate()).padStart(2, '0')
        + ' ' + String(parsed.getHours()).padStart(2, '0')
        + ':' + String(parsed.getMinutes()).padStart(2, '0')
        + ':' + String(parsed.getSeconds()).padStart(2, '0');
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

function refreshAllGroupCounters() {
    const groups = document.querySelectorAll('[data-group-id]');
    const uniqueGroupIds = new Set();

    groups.forEach(function(element) {
        const groupId = element.getAttribute('data-group-id');
        if (groupId && groupId !== 'ungrouped') {
            uniqueGroupIds.add(groupId);
        }
    });

    uniqueGroupIds.forEach(function(groupId) {
        const rows = document.querySelectorAll('.resource-row[data-group-id="' + groupId + '"]');
        const counts = {
            available: 0,
            'not-available': 0,
            unknown: 0
        };

        rows.forEach(function(row) {
            const dot = row.querySelector('[data-role="status-dot"]');
            const status = getStatusFromDot(dot);
            counts[status] += 1;
        });

        updateGroupCounterBadge(groupId, 'available', counts.available);
        updateGroupCounterBadge(groupId, 'not-available', counts['not-available']);
        updateGroupCounterBadge(groupId, 'unknown', counts.unknown);
    });
}

function updateGroupCounterBadge(groupId, status, value) {
    const selector = '[data-group-counter="' + status + '"][data-group-id="' + groupId + '"]';
    const badge = document.querySelector(selector);
    if (!badge) {
        return;
    }
    badge.textContent = String(value);
}

function getStatusFromDot(dot) {
    if (!dot) {
        return 'unknown';
    }
    if (dot.classList.contains('status-available')) {
        return 'available';
    }
    if (dot.classList.contains('status-not-available')) {
        return 'not-available';
    }
    return 'unknown';
}

function initAdminResourceSorting() {
    const sortLists = document.querySelectorAll('.resource-sort-list');
    if (sortLists.length === 0) {
        return;
    }

    sortLists.forEach(function(list) {
        setupSortableList(list);
    });
    syncAllGroupOrderInputs();
    updateEmptyDropHints();
}

function setupSortableList(list) {
    const items = list.querySelectorAll('.resource-sort-item');

    items.forEach(function(item) {
        item.addEventListener('dragstart', function(event) {
            item.classList.add('is-dragging');
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', item.getAttribute('data-resource-id') || '');
        });

        item.addEventListener('dragend', function() {
            item.classList.remove('is-dragging');
            syncAllGroupOrderInputs();
            updateEmptyDropHints();
        });
    });

    list.addEventListener('dragover', function(event) {
        event.preventDefault();
        const dragging = document.querySelector('.resource-sort-item.is-dragging');
        if (!dragging) {
            return;
        }

        const afterElement = getDragAfterElement(list, event.clientY);
        if (!afterElement) {
            list.appendChild(dragging);
            return;
        }
        list.insertBefore(dragging, afterElement);
        syncDraggedRowGroupSelection(dragging, list);
    });

    list.addEventListener('drop', function(event) {
        event.preventDefault();
        syncAllGroupOrderInputs();
        updateEmptyDropHints();
    });
}

function getDragAfterElement(container, y) {
    const draggableElements = Array.from(container.querySelectorAll('.resource-sort-item:not(.is-dragging)'));

    let closest = null;
    let closestOffset = Number.NEGATIVE_INFINITY;

    draggableElements.forEach(function(element) {
        const box = element.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closestOffset) {
            closestOffset = offset;
            closest = element;
        }
    });

    return closest;
}

function syncAllGroupOrderInputs() {
    const lists = document.querySelectorAll('.resource-sort-list');
    lists.forEach(function(list) {
        syncGroupOrderInput(list);
    });
}

function syncGroupOrderInput(list) {
    const card = list.closest('.card');
    if (!card) {
        return;
    }

    const ids = Array.from(list.querySelectorAll('.resource-sort-item[data-resource-id]'))
        .map(function(item) {
            return item.getAttribute('data-resource-id');
        })
        .filter(function(id) {
            return id !== null && id !== '';
        });

    const hiddenInput = card.querySelector('input[name="orderedResourceIds"]');
    if (hiddenInput) {
        hiddenInput.value = ids.join(',');
    }
}

function syncDraggedRowGroupSelection(row, targetList) {
    const groupId = targetList.getAttribute('data-group-id');
    const select = row.querySelector('select[name="groupId"]');
    if (!select) {
        return;
    }

    if (groupId === null || groupId === '' || groupId === 'ungrouped') {
        select.value = '';
    } else {
        select.value = groupId;
    }
}

function updateEmptyDropHints() {
    const lists = document.querySelectorAll('.resource-sort-list');
    lists.forEach(function(list) {
        const placeholder = list.querySelector('.empty-drop-hint');
        if (!placeholder) {
            return;
        }
        const resourceCount = list.querySelectorAll('.resource-sort-item[data-resource-id]').length;
        placeholder.style.display = resourceCount === 0 ? '' : 'none';
    });
}
