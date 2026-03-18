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
    refreshAllGroupCounters();
    initAdminResourceSorting();
});

function initResourceStatusStream() {
    const hasLiveResourceView = document.querySelector('.resource-row[data-resource-id], .resource-detail[data-resource-id]');
    if (!hasLiveResourceView) {
        return;
    }

    const pollIntervalMs = 10000;
    let pollingStarted = false;

    function applySnapshot(updates) {
        if (!Array.isArray(updates)) {
            return;
        }
        updates.forEach(updateResourceRow);
    }

    function startHttpPolling() {
        if (pollingStarted) {
            return;
        }
        pollingStarted = true;

        const fetchSnapshot = function() {
            fetch('/api/resources/status-updates', {
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
                });
        };

        fetchSnapshot();
        window.setInterval(fetchSnapshot, pollIntervalMs);
    }

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
