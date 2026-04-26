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

function waitForNextRenderStep(delayMs) {
    return new Promise(function(resolve) {
        window.setTimeout(resolve, delayMs);
    });
}

function initializeViewModeSwitcher() {
    const switcher = document.querySelector('[data-role="view-mode-switcher"]');
    if (!switcher) {
        return;
    }

    const buttons = switcher.querySelectorAll('button[data-view-mode]');
    const savedViewMode = localStorage.getItem('viewMode');

    function normalizeViewMode(mode) {
        return mode === 'cards' ? 'cards' : 'timeline';
    }

    function setViewMode(mode) {
        const normalizedMode = normalizeViewMode(mode);

        // Update button states
        buttons.forEach(function(btn) {
            const isActive = btn.getAttribute('data-view-mode') === normalizedMode;
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        });

        // Update visibility of resource containers
        document.querySelectorAll('[data-view]').forEach(function(container) {
            const view = container.getAttribute('data-view');
            if (view === normalizedMode) {
                container.style.display = '';
            } else {
                container.style.display = 'none';
            }
        });

        // Save preference
        localStorage.setItem('viewMode', normalizedMode);
    }

    // Set initial view mode
    setViewMode(normalizeViewMode(savedViewMode));

    // Add click listeners to buttons
    buttons.forEach(function(button) {
        button.addEventListener('click', function() {
            const mode = button.getAttribute('data-view-mode');
            setViewMode(mode);
        });
    });
}

function initializeResourceCardLinks() {
    const cards = document.querySelectorAll('.resource-card[data-resource-url]');
    if (cards.length === 0) {
        return;
    }

    cards.forEach(function(card) {
        const url = card.getAttribute('data-resource-url');
        if (!url) {
            return;
        }

        card.setAttribute('role', 'link');
        card.setAttribute('tabindex', '0');

        card.addEventListener('click', function(event) {
            // Keep native behavior for explicit links/buttons inside the card.
            const interactive = event.target.closest('a, button, input, select, textarea, label');
            if (interactive) {
                return;
            }
            window.location.href = url;
        });

        card.addEventListener('keydown', function(event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                window.location.href = url;
            }
        });
    });
}

function initializeOutageSinceCounters() {
    const counters = document.querySelectorAll('[data-role="outage-since-counter"]');
    if (counters.length === 0) {
        return;
    }

    function formatElapsed(totalSeconds) {
        const safeSeconds = Math.max(0, totalSeconds);
        const days = Math.floor(safeSeconds / 86400);
        const hours = Math.floor((safeSeconds % 86400) / 3600);
        const minutes = Math.floor((safeSeconds % 3600) / 60);
        const seconds = safeSeconds % 60;

        if (days > 0) {
            return days + 'd ' + hours + 'h ' + minutes + 'm ' + seconds + 's';
        }
        if (hours > 0) {
            return hours + 'h ' + minutes + 'm ' + seconds + 's';
        }
        if (minutes > 0) {
            return minutes + 'm ' + seconds + 's';
        }
        return seconds + 's';
    }

    function parseStart(raw) {
        if (!raw) {
            return null;
        }
        const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T');
        const parsed = new Date(normalized);
        if (Number.isNaN(parsed.getTime())) {
            return null;
        }
        return parsed;
    }

    function refresh() {
        const now = Date.now();
        counters.forEach(function(counter) {
            const startRaw = counter.getAttribute('data-outage-start');
            const startDate = parseStart(startRaw);
            if (!startDate) {
                counter.textContent = '-';
                return;
            }
            const elapsedSeconds = Math.floor((now - startDate.getTime()) / 1000);
            counter.textContent = formatElapsed(elapsedSeconds);
        });
    }

    refresh();
    window.setInterval(refresh, 1000);
}

document.addEventListener('DOMContentLoaded', function() {
    const saved = localStorage.getItem('theme') || 'dark';
    document.documentElement.setAttribute('data-bs-theme', saved);
    initializeViewModeSwitcher();
    initializeResourceCardLinks();
    initializeOutageSinceCounters();
    initializeTimelineLabels();
    initializeLatencyChartsFromDom();
    initResourceStatusStream();
    refreshAllGroupCounters();
    initAdminResourceSorting();
});

function initResourceStatusStream() {
    const hasLiveResourceView = document.querySelector('.resource-row[data-resource-id], .resource-detail[data-resource-id]');
    if (!hasLiveResourceView) {
        return;
    }

    const preferPollingOverSse = false;

    const rangeControls = document.querySelector('[data-role="timeline-range-controls"]');
    const rangeButtons = rangeControls ? rangeControls.querySelectorAll('[data-timeline-hours]') : [];
    const rangeLabel = document.querySelector('[data-role="timeline-range-label"]');
    const rangeStartLabels = document.querySelectorAll('[data-role="timeline-range-start"]');
    const loadingIndicator = document.querySelector('[data-role="timeline-loading-indicator"]');
    const cardsLoadingIndicator = document.querySelector('[data-role="cards-loading-indicator"]');
    const hasRangeSelector = rangeButtons.length > 0;
    const pollIntervalMs = 10000;
    const progressiveRenderDelayMs = 15;
    const snapshotParallelism = 4;
    let pollingStarted = false;
    let currentTimelineHours = 24;
    let activeSnapshotRenderId = 0;

    markResourceContainersLoading();

    function isCardsViewActive() {
        const visibleCardsContainer = document.querySelector('[data-view="cards"]:not([style*="display: none"])');
        return !!visibleCardsContainer;
    }

    function markResourceContainersLoading() {
        document.querySelectorAll('.resource-row[data-resource-id], .resource-detail[data-resource-id], [data-view="cards"] [data-resource-id]')
            .forEach(function(container) {
                container.classList.add('resource-loading');
                setRowChecking(container, true);
            });
    }

    function clearResourceLoadingStates() {
        document.querySelectorAll('.resource-loading').forEach(function(container) {
            container.classList.remove('resource-loading');
            setRowChecking(container, false);
        });
    }

    function hasLoadingResourceContainers() {
        return document.querySelector('.resource-loading') !== null;
    }

    function renderSnapshotSequentially(updates, renderId) {
        return updates.reduce(function(chain, update) {
            return chain.then(function() {
                if (renderId !== activeSnapshotRenderId) {
                    return;
                }

                updateResourceRow(update);

                return waitForNextRenderStep(progressiveRenderDelayMs);
            });
        }, Promise.resolve()).then(function() {
            if (renderId !== activeSnapshotRenderId) {
                return;
            }

            clearResourceLoadingStates();
        });
    }

    function applySnapshot(updates) {
        if (!Array.isArray(updates)) {
            return Promise.resolve();
        }

        if (updates.length === 0) {
            clearResourceLoadingStates();
            updateSnapshotCounts(updates);
            return Promise.resolve();
        }

        if (!hasLoadingResourceContainers()) {
            updates.forEach(updateResourceRow);
            updateSnapshotCounts(updates);
            return Promise.resolve();
        }

        activeSnapshotRenderId += 1;
        return renderSnapshotSequentially(updates, activeSnapshotRenderId)
            .then(function() {
                updateSnapshotCounts(updates);
            });
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
        const cardsActive = isCardsViewActive();
        if (loadingIndicator) {
            loadingIndicator.hidden = !loading || cardsActive;
        }
        if (cardsLoadingIndicator) {
            cardsLoadingIndicator.hidden = !loading || !cardsActive;
        }
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

    function collectUniqueResourceIds() {
        const ids = new Set();
        document.querySelectorAll('[data-resource-id]').forEach(function(container) {
            const rawId = container.getAttribute('data-resource-id');
            if (!rawId) {
                return;
            }
            ids.add(rawId);
        });
        return Array.from(ids);
    }

    function buildResourceSnapshotUrl(resourceId) {
        return '/api/resources/' + encodeURIComponent(String(resourceId))
            + '/status-update?hours=' + encodeURIComponent(String(currentTimelineHours));
    }

    function fetchSnapshot(options) {
        const showLoading = options && options.showLoading === true;
        const shouldShowLoading = showLoading || isCardsViewActive();
        const isInitialRender = hasLoadingResourceContainers();
        const shouldMarkResourcesLoading = isInitialRender || showLoading;
        const requestRenderId = activeSnapshotRenderId + 1;
        const resourceIds = collectUniqueResourceIds();

        activeSnapshotRenderId = requestRenderId;
        if (shouldMarkResourcesLoading) {
            markResourceContainersLoading();
        }

        if (shouldShowLoading) {
            setTimelineLoading(true);
        }

        if (resourceIds.length === 0) {
            clearResourceLoadingStates();
            updateSnapshotCounts([]);
            return Promise.resolve().finally(function() {
                if (shouldShowLoading) {
                    setTimelineLoading(false);
                }
            });
        }

        const updates = [];
        let nextResourceIndex = 0;

        function fetchResourceUpdate(resourceId) {
            return fetch(buildResourceSnapshotUrl(resourceId), {
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
                .then(function(update) {
                    if (requestRenderId !== activeSnapshotRenderId || !update) {
                        return;
                    }
                    updates.push(update);
                    updateResourceRow(update);
                    if (isInitialRender) {
                        return waitForNextRenderStep(progressiveRenderDelayMs);
                    }
                })
                .catch(function() {
                    // Continue with remaining resources.
                });
        }

        function worker() {
            if (requestRenderId !== activeSnapshotRenderId) {
                return Promise.resolve();
            }

            if (nextResourceIndex >= resourceIds.length) {
                return Promise.resolve();
            }

            const resourceId = resourceIds[nextResourceIndex];
            nextResourceIndex += 1;

            return fetchResourceUpdate(resourceId).then(worker);
        }

        const workers = [];
        const workerCount = Math.min(snapshotParallelism, resourceIds.length);
        for (let i = 0; i < workerCount; i += 1) {
            workers.push(worker());
        }

        return Promise.all(workers)
            .then(function() {
                if (requestRenderId !== activeSnapshotRenderId) {
                    return;
                }
                clearResourceLoadingStates();
                updateSnapshotCounts(updates);
            })
            .catch(function() {
                // Retry on next interval.
            })
            .finally(function() {
                if (shouldShowLoading) {
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

    if (preferPollingOverSse || typeof EventSource === 'undefined') {
        startHttpPolling();
        return;
    }

    const eventSource = new EventSource('/api/resources/stream');

    eventSource.onopen = function() {
        fetchSnapshotWithOptionalLoading(false);
    };

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

    // Do not wait for the initial SSE snapshot before painting timelines.
    fetchSnapshotWithOptionalLoading(true);
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
        updateCardStatus(container, update.currentStatus);
        updateTimeline(container, update.timelineBlocks);
        updateUptime(container, update.uptimePercentage);
        updateOutageBadge(container, update.activeOutageSince || null);
        updateLatencyLabel(container, update.timelineBlocks);
        container.classList.remove('resource-loading');
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
        + ', .resource-detail[data-resource-id="' + resourceId + '"]'
        + ', [data-resource-id="' + resourceId + '"]';
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
        const latencyMs = resolveTimelineBlockLatency(block);
        const dnsLatencyMs = resolveTimelineBlockDnsLatency(block);
        const connectLatencyMs = resolveTimelineBlockConnectLatency(block);
        const tlsLatencyMs = resolveTimelineBlockTlsLatency(block);

        if (latencyMs !== null) {
            blockElement.dataset.latencyMs = String(latencyMs);
        }
        if (dnsLatencyMs !== null) {
            blockElement.dataset.dnsLatencyMs = String(dnsLatencyMs);
        }
        if (connectLatencyMs !== null) {
            blockElement.dataset.connectLatencyMs = String(connectLatencyMs);
        }
        if (tlsLatencyMs !== null) {
            blockElement.dataset.tlsLatencyMs = String(tlsLatencyMs);
        }

        blockElement.title = buildTimelineTooltip(status, resolveTimelineBlockTimestamp(block), block);
        fragment.appendChild(blockElement);
    });

    container.replaceChildren(fragment);
    // Skip chart re-render when the detail page is using fine-grained latency samples fetched
    // from the API; the SSE timeline blocks are coarser and should not overwrite that data.
    const latencyPanel = row.querySelector('[data-role="latency-panel"]');
    const usingSamplesMode = latencyPanel && latencyPanel._latencyRawSamples !== undefined;
    if (!usingSamplesMode) {
        renderLatencyChart(row, timelineBlocks);
    }
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

function resolveTimelineBlockLatency(block) {
    if (block && typeof block === 'object') {
        return parseLatencyValue(block.latencyMs);
    }
    return null;
}

function resolveTimelineBlockDnsLatency(block) {
    if (block && typeof block === 'object') {
        return parseLatencyValue(block.dnsResolutionMs);
    }
    return null;
}

function resolveTimelineBlockConnectLatency(block) {
    if (block && typeof block === 'object') {
        return parseLatencyValue(block.connectMs);
    }
    return null;
}

function resolveTimelineBlockTlsLatency(block) {
    if (block && typeof block === 'object') {
        return parseLatencyValue(block.tlsHandshakeMs);
    }
    return null;
}

function parseLatencyValue(value) {
    if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
        return value;
    }
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        if (Number.isFinite(parsed) && parsed >= 0) {
            return parsed;
        }
    }
    return null;
}

function buildTimelineTooltip(status, timestamp, block) {
    const normalizedStatus = normalizeStatus(status);
    const formattedTimestamp = formatTimelineTimestamp(timestamp);
    const latencyMs = resolveTimelineBlockLatency(block);
    const dnsLatencyMs = resolveTimelineBlockDnsLatency(block);
    const connectLatencyMs = resolveTimelineBlockConnectLatency(block);
    const tlsLatencyMs = resolveTimelineBlockTlsLatency(block);

    const lines = [];
    if (!formattedTimestamp) {
        lines.push(normalizedStatus);
    } else {
        lines.push(normalizedStatus + ' · ' + formattedTimestamp);
    }

    if (latencyMs !== null) {
        lines.push('Latency: ' + formatLatencyMs(latencyMs));
    }
    if (dnsLatencyMs !== null) {
        lines.push('DNS: ' + formatLatencyMs(dnsLatencyMs));
    }
    if (connectLatencyMs !== null) {
        lines.push('Connect: ' + formatLatencyMs(connectLatencyMs));
    }
    if (tlsLatencyMs !== null) {
        lines.push('TLS: ' + formatLatencyMs(tlsLatencyMs));
    }

    return lines.join('\n');
}

function formatLatencyMs(value) {
    return Math.round(value) + ' ms';
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

function updateCardStatus(row, status) {
    var card = row.querySelector('.resource-card');
    if (!card) {
        return;
    }
    var normalized = normalizeStatus(status);
    card.classList.remove('status-available', 'status-not-available', 'status-unknown');
    card.classList.add('status-' + normalized);
    var stateLabel = row.querySelector('[data-role="card-status"]');
    if (stateLabel) {
        stateLabel.textContent = normalized;
    }
}

function updateOutageBadge(row, activeOutageSince) {
    var badge = row.querySelector('[data-role="outage-badge"]');
    if (!badge) {
        return;
    }
    if (activeOutageSince) {
        var counter = badge.querySelector('[data-role="outage-since-counter"]');
        if (counter) {
            counter.setAttribute('data-outage-start', activeOutageSince);
        }
        badge.removeAttribute('hidden');
    } else {
        badge.setAttribute('hidden', '');
    }
}

function updateSnapshotCounts(updates) {
    if (!Array.isArray(updates)) {
        return;
    }
    var available = 0, down = 0, unknown = 0;
    updates.forEach(function(u) {
        var s = normalizeStatus(u && u.currentStatus);
        if (s === 'available') { available++; }
        else if (s === 'not-available') { down++; }
        else { unknown++; }
    });
    var el;
    el = document.querySelector('[data-role="snapshot-available"]');
    if (el) { el.textContent = String(available); }
    el = document.querySelector('[data-role="snapshot-down"]');
    if (el) { el.textContent = String(down); }
    el = document.querySelector('[data-role="snapshot-unknown"]');
    if (el) { el.textContent = String(unknown); }
}

function updateUptime(row, uptimePercentage) {
    const uptimeElement = row.querySelector('.resource-uptime');
    if (!uptimeElement || typeof uptimePercentage !== 'number') {
        return;
    }

    uptimeElement.textContent = uptimePercentage.toFixed(1) + '%';
}

function updateLatencyLabel(row, timelineBlocks) {
    const label = row.querySelector('[data-role="resource-latency"]');
    if (!label) { return; }
    const blocks = Array.isArray(timelineBlocks) ? timelineBlocks : [];
    const values = blocks
        .map(function(b) { return resolveTimelineBlockLatency(b); })
        .filter(function(v) { return v !== null; });
    if (values.length === 0) {
        label.textContent = '';
        return;
    }
    const latest = values[values.length - 1];
    const avg = Math.round(values.reduce(function(s, v) { return s + v; }, 0) / values.length);
    label.textContent = formatLatencyMs(latest) + ' avg ' + formatLatencyMs(avg);
}

function normalizeStatus(status) {
    if (status === 'available' || status === 'not-available' || status === 'unknown') {
        return status;
    }
    return 'unknown';
}

function fetchAndRenderLatencySamples(row, hours) {
    var resourceId = row.getAttribute('data-resource-id');
    if (!resourceId) { return; }
    fetch('/api/resources/' + encodeURIComponent(resourceId) + '/latency-samples?hours=' + encodeURIComponent(String(hours || 24)), {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        cache: 'no-store'
    })
    .then(function(resp) {
        if (!resp.ok) { throw new Error('HTTP ' + resp.status); }
        return resp.json();
    })
    .then(function(samples) {
        var panel = row.querySelector('[data-role="latency-panel"]');
        if (panel) {
            panel._latencyRawSamples = Array.isArray(samples) ? samples : [];
        }
        renderLatencyChart(row, panel ? panel._latencyRawSamples : []);
    })
    .catch(function() { /* chart stays as-is */ });
}

function downsampleLatency(samples, targetCount) {
    if (!Array.isArray(samples) || samples.length <= targetCount) { return samples; }
    var result = [];
    var groupSize = samples.length / targetCount;
    for (var i = 0; i < targetCount; i++) {
        var start = Math.floor(i * groupSize);
        var end = Math.min(samples.length, Math.floor((i + 1) * groupSize));
        if (start >= samples.length) { break; }
        var group = samples.slice(start, end);
        var sum = 0;
        for (var j = 0; j < group.length; j++) { sum += group[j].latencyMs; }
        var mid = group[Math.floor(group.length / 2)];
        result.push({
            latencyMs: Math.round(sum / group.length),
            checkedAt: mid.checkedAt || null,
            dnsResolutionMs: mid.dnsResolutionMs,
            connectMs: mid.connectMs,
            tlsHandshakeMs: mid.tlsHandshakeMs
        });
    }
    return result;
}

function initializeLatencyChartsFromDom() {
    const rows = document.querySelectorAll('.resource-detail[data-resource-id], .resource-row[data-resource-id]');
    rows.forEach(function(row) {
        if (!row.querySelector('[data-role="latency-chart"]')) {
            return;
        }
        const panel = row.querySelector('[data-role="latency-panel"]');
        if (panel) {
            initLatencyZoomControls(row, panel);
        }
        const wrapper = row.querySelector('[data-role="latency-chart-wrapper"]');
        if (wrapper) {
            initLatencyChartDrag(wrapper);
        }
        const svgEl = row.querySelector('[data-role="latency-chart"]');
        const tooltipEl = row.querySelector('[data-role="latency-tooltip"]');
        if (svgEl && tooltipEl && wrapper) {
            initLatencyTooltip(svgEl, tooltipEl, wrapper);
        }
        // Fetch for the currently active range
        var currentHours = 24;
        var activeBtn = document.querySelector('[data-role="timeline-range-controls"] .btn.active[data-timeline-hours]');
        if (activeBtn) {
            currentHours = parseInt(activeBtn.getAttribute('data-timeline-hours'), 10) || 24;
        }
        fetchAndRenderLatencySamples(row, currentHours);
        // Re-fetch when the user switches range
        document.querySelectorAll('[data-role="timeline-range-controls"] [data-timeline-hours]').forEach(function(btn) {
            btn.addEventListener('click', function() {
                var hours = parseInt(btn.getAttribute('data-timeline-hours'), 10) || 24;
                fetchAndRenderLatencySamples(row, hours);
            });
        });
    });
}

function initLatencyTooltip(svg, tooltipEl, wrapper) {
    svg.addEventListener('mouseover', function(e) {
        if (!e.target.classList.contains('latency-dot')) {
            tooltipEl.hidden = true;
            return;
        }
        var samples = svg._latencySamples;
        if (!samples) { return; }
        var idx = parseInt(e.target.dataset.idx, 10);
        var sample = samples[idx];
        if (!sample) { return; }
        var svgRect = svg.getBoundingClientRect();
        var cx = parseFloat(e.target.getAttribute('cx'));
        var cy = parseFloat(e.target.getAttribute('cy'));
        var xPx = (cx / 900) * svgRect.width;
        var yPx = (cy / 180) * svgRect.height;
        tooltipEl.innerHTML = '';
        var valDiv = document.createElement('div');
        valDiv.className = 'latency-tooltip-value';
        valDiv.textContent = formatLatencyMs(sample.latencyMs);
        tooltipEl.appendChild(valDiv);
        if (sample.checkedAt) {
            var timeDiv = document.createElement('div');
            timeDiv.className = 'latency-tooltip-time';
            timeDiv.textContent = formatCheckedAtShort(sample.checkedAt);
            tooltipEl.appendChild(timeDiv);
        }
        tooltipEl.style.left = xPx + 'px';
        tooltipEl.style.top = yPx + 'px';
        tooltipEl.hidden = false;
    });
    svg.addEventListener('mouseleave', function() {
        tooltipEl.hidden = true;
    });
}

function initLatencyZoomControls(row, panel) {
    const zoomIn = panel.querySelector('[data-role="latency-zoom-in"]');
    const zoomOut = panel.querySelector('[data-role="latency-zoom-out"]');
    const zoomReset = panel.querySelector('[data-role="latency-zoom-reset"]');
    if (!zoomIn || !zoomOut || !zoomReset) { return; }

    panel.dataset.latencyZoom = '1';
    zoomOut.disabled = true;

    function applyZoom(newZoom) {
        const clamped = Math.max(1, Math.min(8, newZoom));
        panel.dataset.latencyZoom = String(clamped);
        zoomOut.disabled = clamped <= 1;
        zoomIn.disabled = clamped >= 8;
        renderLatencyChart(row, panel._latencyRawSamples || []);
    }

    zoomIn.addEventListener('click', function() {
        applyZoom(parseInt(panel.dataset.latencyZoom || '1', 10) * 2);
    });
    zoomOut.addEventListener('click', function() {
        applyZoom(Math.max(1, Math.round(parseInt(panel.dataset.latencyZoom || '1', 10) / 2)));
    });
    zoomReset.addEventListener('click', function() {
        applyZoom(1);
    });
}

function initLatencyChartDrag(wrapper) {
    let dragging = false;
    let startX = 0;
    let startScroll = 0;
    wrapper.addEventListener('mousedown', function(e) {
        if (e.target.closest('button')) { return; }
        dragging = true;
        startX = e.clientX;
        startScroll = wrapper.scrollLeft;
        wrapper.style.cursor = 'grabbing';
        e.preventDefault();
    });
    window.addEventListener('mousemove', function(e) {
        if (!dragging) { return; }
        wrapper.scrollLeft = startScroll - (e.clientX - startX);
    });
    window.addEventListener('mouseup', function() {
        if (dragging) {
            dragging = false;
            wrapper.style.cursor = '';
        }
    });
}

function renderLatencyChart(row, rawSamples) {
    const svg = row.querySelector('[data-role="latency-chart"]');
    const lineEl = row.querySelector('[data-role="latency-line"]');
    const areaEl = row.querySelector('[data-role="latency-area"]');
    const summary = row.querySelector('[data-role="latency-summary"]');
    const minLabel = row.querySelector('[data-role="latency-min"]');
    const maxLabel = row.querySelector('[data-role="latency-max"]');
    const timeAxisEl = row.querySelector('[data-role="latency-time-axis"]');

    if (!svg || !lineEl || !areaEl || !summary || !minLabel || !maxLabel) {
        return;
    }

    // Apply zoom: scale SVG width so the scroll wrapper can clip and scroll it.
    const panel = row.querySelector('[data-role="latency-panel"]');
    const zoomLevel = panel ? Math.max(1, parseInt(panel.dataset.latencyZoom || '1', 10)) : 1;

    // Downsample raw samples based on zoom: more zoom = more visible points
    const allSamples = (Array.isArray(rawSamples) ? rawSamples : [])
        .filter(function(s) { return s && s.latencyMs !== null && s.latencyMs !== undefined; });
    const targetPoints = 90 * zoomLevel;
    const samples = downsampleLatency(allSamples, targetPoints);
    const wrapper = row.querySelector('[data-role="latency-chart-wrapper"]');
    const containerWidth = wrapper ? wrapper.clientWidth : 0;
    const chartPxWidth = containerWidth > 0 ? containerWidth * zoomLevel : null;
    if (chartPxWidth) {
        svg.style.width = chartPxWidth + 'px';
        if (timeAxisEl) { timeAxisEl.style.width = chartPxWidth + 'px'; }
    } else {
        svg.style.width = '';
        if (timeAxisEl) { timeAxisEl.style.width = ''; }
    }

    if (samples.length === 0) {
        lineEl.setAttribute('d', '');
        areaEl.setAttribute('d', '');
        var dotsElClear = row.querySelector('[data-role="latency-dots"]');
        if (dotsElClear) { dotsElClear.innerHTML = ''; }
        svg._latencySamples = [];
        if (timeAxisEl) { timeAxisEl.innerHTML = ''; }
        summary.textContent = 'No latency samples yet';
        minLabel.textContent = '0 ms';
        maxLabel.textContent = '0 ms';
        return;
    }

    const values = samples.map(function(s) { return s.latencyMs; });
    const minValue = Math.min.apply(null, values);
    const maxValue = Math.max.apply(null, values);
    const latestValue = samples[samples.length - 1].latencyMs;
    const p95Value = percentile(values, 95);

    summary.textContent = 'Latest ' + formatLatencyMs(latestValue)
        + ' · p95 ' + formatLatencyMs(p95Value)
        + ' · ' + samples.length + ' samples';
    minLabel.textContent = formatLatencyMs(minValue);
    maxLabel.textContent = formatLatencyMs(maxValue);

    const svgW = 900;
    const chartH = 180;
    const topPad = 10;
    const botPad = 10;
    const xDen = Math.max(samples.length - 1, 1);

    let chartMin = minValue;
    let chartMax = maxValue;
    if (chartMax === chartMin) {
        chartMax = chartMin + 1;
    } else {
        const pad = Math.max((chartMax - chartMin) * 0.08, 1);
        chartMin = Math.max(0, chartMin - pad);
        chartMax = chartMax + pad;
    }

    const points = samples.map(function(s, i) {
        const x = (i / xDen) * svgW;
        const norm = (s.latencyMs - chartMin) / (chartMax - chartMin);
        const y = chartH - botPad - norm * (chartH - topPad - botPad);
        return { x: x, y: y };
    });

    const linePath = points.map(function(p, i) {
        return (i === 0 ? 'M' : 'L') + p.x.toFixed(2) + ',' + p.y.toFixed(2);
    }).join(' ');
    lineEl.setAttribute('d', linePath);

    const areaPath = linePath
        + ' L' + points[points.length - 1].x.toFixed(2) + ',' + (chartH - botPad)
        + ' L' + points[0].x.toFixed(2) + ',' + (chartH - botPad) + ' Z';
    areaEl.setAttribute('d', areaPath);

    const dotsEl = row.querySelector('[data-role="latency-dots"]');
    if (dotsEl) {
        dotsEl.innerHTML = '';
        var svgNS = 'http://www.w3.org/2000/svg';
        points.forEach(function(p, i) {
            var circle = document.createElementNS(svgNS, 'circle');
            circle.setAttribute('cx', p.x.toFixed(2));
            circle.setAttribute('cy', p.y.toFixed(2));
            circle.setAttribute('r', '3.5');
            circle.setAttribute('class', 'latency-dot');
            circle.dataset.idx = String(i);
            dotsEl.appendChild(circle);
        });
    }
    svg._latencySamples = samples;

    // Time axis: HTML labels below the SVG (inside the scroll wrapper so they scroll together)
    if (timeAxisEl) {
        timeAxisEl.innerHTML = '';
        const hasTimestamps = samples.some(function(s) { return s.checkedAt !== null; });
        if (hasTimestamps) {
            const maxLabels = Math.min(7, samples.length);
            for (let i = 0; i < maxLabels; i++) {
                const sIdx = samples.length <= 1 ? 0 : Math.round(i * (samples.length - 1) / (maxLabels - 1));
                const sample = samples[Math.min(sIdx, samples.length - 1)];
                const leftPct = (sIdx / xDen) * 100;
                const timeStr = formatCheckedAtShort(sample.checkedAt);
                if (!timeStr) { continue; }
                const span = document.createElement('span');
                span.textContent = timeStr;
                span.style.left = leftPct.toFixed(2) + '%';
                if (i === 0) { span.classList.add('axis-label-first'); }
                if (i === maxLabels - 1) { span.classList.add('axis-label-last'); }
                timeAxisEl.appendChild(span);
            }
        }
    }
}

function percentile(values, percentileRank) {
    if (!Array.isArray(values) || values.length === 0) {
        return 0;
    }
    const sorted = values.slice().sort(function(a, b) { return a - b; });
    const rank = Math.min(sorted.length - 1, Math.max(0, Math.ceil((percentileRank / 100) * sorted.length) - 1));
    return sorted[rank];
}

function formatCheckedAtShort(checkedAt) {
    if (!checkedAt) { return null; }
    // 'yyyy-MM-dd HH:mm:ss' -> 'HH:mm:ss'
    const spaceIdx = checkedAt.indexOf(' ');
    return spaceIdx >= 0 ? checkedAt.substring(spaceIdx + 1) : checkedAt;
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
