/**
 * Pi Weather Dashboard — app.js
 * Warm Instrumentation design. Chart.js 4.x + EventSource SSE.
 */

(function () {
  'use strict';

  /* ─── DOM refs ─── */
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  const alertsBar   = $('#alerts-bar');
  const alertsText  = $('#alerts-text');
  const valTemp     = $('#val-temp');
  const valHumid    = $('#val-humid');
  const valPress    = $('#val-press');
  const valLux      = $('#val-lux');
  const advisoryCard= $('#latest-advisory');
  const advRisk     = $('#advisory-risk');
  const advSummary  = $('#advisory-summary');
  const advRec      = $('#advisory-recommendation');
  const advTime     = $('#advisory-time');
  const toast       = $('#toast');
  const toastDot    = $('#toast-dot');
  const toastMsg    = $('#toast-msg');
  const toastClose  = $('#toast-close');
  const historyList = $('#history-list');
  const loadMoreWrap= $('#load-more-wrap');
  const loadMoreBtn = $('#load-more-btn');
  const pageDash    = $('#page-dashboard');
  const pageTrends  = $('#page-trends');
  const pageHistory = $('#page-history');

  /* ─── State ─── */
  const MAX_CHART_POINTS = 50;
  const chartLabels = [];       // ISO timestamp strings
  const chartData = {
    temp: [],
    humidity: [],
    pressure: [],
    lux: []
  };

  let currentPage = 0;
  let totalPages = 0;

  /* ─── Trends State ─── */
  var trendsChart = null;
  var trendsChartReady = false;
  var activePreset = '24h';

  /* ─── Sidebar Navigation ─── */
  $$('.sidebar-nav a').forEach(function (link) {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      var page = this.dataset.page;

      // Update active link
      $$('.sidebar-nav a').forEach(function (a) { a.classList.remove('active'); });
      this.classList.add('active');

      // Toggle pages
      $$('.page').forEach(function (p) { p.classList.remove('active'); });
      if (page === 'dashboard') {
        pageDash.classList.add('active');
      } else if (page === 'trends') {
        pageTrends.classList.add('active');
        initTrendsPage();
      } else if (page === 'history') {
        pageHistory.classList.add('active');
        if (currentPage === 0) loadAdvisoryPage(1);
      }
    });
  });

  /* ─── Toast ─── */
  var toastTimer = null;

  function showToast(message, riskLevel) {
    if (toastTimer) clearTimeout(toastTimer);
    toastMsg.textContent = message;

    // Color the dot based on risk level
    var colorMap = {
      'LOW':      'var(--risk-low)',
      'MODERATE': 'var(--risk-moderate)',
      'HIGH':     'var(--risk-high)',
      'EXTREME':  'var(--risk-extreme)'
    };
    toastDot.style.background = colorMap[riskLevel] || 'var(--risk-low)';

    toast.classList.add('show');
    toastTimer = setTimeout(function () {
      toast.classList.remove('show');
    }, 5000);
  }

  toastClose.addEventListener('click', function () {
    toast.classList.remove('show');
    if (toastTimer) clearTimeout(toastTimer);
  });

  /* ─── Formatting ─── */
  function fmtNum(val, decimals) {
    if (val === null || val === undefined) return '--';
    return Number(val).toFixed(decimals || 1);
  }

  function fmtTime(isoStr) {
    if (!isoStr) return '--';
    var d = new Date(isoStr);
    return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function fmtTimeFull(isoStr) {
    if (!isoStr) return '--';
    var d = new Date(isoStr);
    return d.toLocaleString('en-US', {
      month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  /* ─── Update Reading Cards ─── */
  function updateReadingCards(record) {
    valTemp.textContent  = fmtNum(record.temperature, 1);
    valHumid.textContent = fmtNum(record.humidity, 1);
    valPress.textContent = fmtNum(record.pressure, 1);
    valLux.textContent   = fmtNum(record.lux, 0);
  }

  /* ─── Update Alerts Bar ─── */
  function updateAlertsBar(record) {
    var alerts = [];
    if (record.alerts) {
      try {
        alerts = JSON.parse(record.alerts);
      } catch (_) {
        // alerts may be a plain string
        alerts = [record.alerts];
      }
    }

    if (alerts.length > 0) {
      alertsText.textContent = alerts.join('  |  ');
      alertsBar.classList.add('has-alerts');
    } else {
      alertsBar.classList.remove('has-alerts');
    }
  }

  /* ─── Update Advisory Card ─── */
  function updateAdvisoryCard(advisory) {
    var risk = advisory.riskLevel || 'LOW';
    advRisk.textContent = risk;
    advRisk.className = 'risk-badge risk-' + risk;
    advisoryCard.className = 'advisory-card risk-' + risk;

    advSummary.textContent = advisory.summary || '';
    advRec.textContent = advisory.recommendation || '';
    advTime.textContent = fmtTimeFull(advisory.createdAt);
  }

  /* ─── Chart Setup ─── */
  var ctx = $('#trendChart').getContext('2d');

  var trendChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: chartLabels,
      datasets: [
        {
          label: 'Temperature (°C)',
          data: chartData.temp,
          borderColor: '#ff6b35',
          backgroundColor: 'rgba(255,107,53,0.08)',
          borderWidth: 2,
          pointRadius: 0,
          pointHitRadius: 8,
          tension: 0.3,
          yAxisID: 'y',
          spanGaps: false
        },
        {
          label: 'Humidity (%)',
          data: chartData.humidity,
          borderColor: '#4ecdc4',
          backgroundColor: 'rgba(78,205,196,0.08)',
          borderWidth: 2,
          pointRadius: 0,
          pointHitRadius: 8,
          tension: 0.3,
          yAxisID: 'y',
          spanGaps: false
        },
        {
          label: 'Pressure (hPa)',
          data: chartData.pressure,
          borderColor: '#f7dc6f',
          backgroundColor: 'rgba(247,220,111,0.08)',
          borderWidth: 2,
          pointRadius: 0,
          pointHitRadius: 8,
          tension: 0.3,
          yAxisID: 'y',
          spanGaps: false
        },
        {
          label: 'Light (lux)',
          data: chartData.lux,
          borderColor: '#ffeaa7',
          backgroundColor: 'rgba(255,234,167,0.08)',
          borderWidth: 2,
          pointRadius: 0,
          pointHitRadius: 8,
          tension: 0.3,
          yAxisID: 'y1',
          spanGaps: false
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          position: 'top',
          align: 'start',
          labels: {
            boxWidth: 14,
            boxHeight: 2,
            padding: 16,
            color: '#9b8c7d',
            font: {
              family: "'DM Mono', monospace",
              size: 10
            },
            usePointStyle: false
          }
        },
        tooltip: {
          backgroundColor: '#242629',
          titleColor: '#e8d5c4',
          bodyColor: '#9b8c7d',
          borderColor: '#2e3035',
          borderWidth: 1,
          titleFont: {
            family: "'DM Mono', monospace",
            size: 11
          },
          bodyFont: {
            family: "'Space Mono', monospace",
            size: 11
          },
          callbacks: {
            title: function (items) {
              if (items.length === 0) return '';
              return fmtTimeFull(items[0].label);
            }
          }
        }
      },
      scales: {
        x: {
          display: true,
          ticks: {
            color: '#5d4f42',
            font: { family: "'DM Mono', monospace", size: 9 },
            maxTicksLimit: 8,
            callback: function (_val, index) {
              return fmtTime(chartLabels[index]);
            }
          },
          grid: {
            color: 'rgba(46,48,53,0.5)',
            drawBorder: false
          }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: '°C / % / hPa',
            color: '#9b8c7d',
            font: { family: "'DM Mono', monospace", size: 9 }
          },
          ticks: {
            color: '#5d4f42',
            font: { family: "'DM Mono', monospace", size: 9 }
          },
          grid: {
            color: 'rgba(46,48,53,0.5)',
            drawBorder: false
          }
        },
        y1: {
          type: 'logarithmic',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'lux (log)',
            color: '#9b8c7d',
            font: { family: "'DM Mono', monospace", size: 9 }
          },
          ticks: {
            color: '#5d4f42',
            font: { family: "'DM Mono', monospace", size: 9 },
            callback: function (value) {
              if (value >= 1000) return (value / 1000).toFixed(0) + 'k';
              return value;
            }
          },
          grid: {
            display: false
          },
          min: 1
        }
      }
    }
  });

  /* ─── Chart Data Helpers ─── */
  function pushChartPoint(record) {
    chartLabels.push(record.createdAt);
    chartData.temp.push(record.temperature);
    chartData.humidity.push(record.humidity);
    chartData.pressure.push(record.pressure);
    chartData.lux.push(record.lux);

    // Trim to max points
    while (chartLabels.length > MAX_CHART_POINTS) {
      chartLabels.shift();
      chartData.temp.shift();
      chartData.humidity.shift();
      chartData.pressure.shift();
      chartData.lux.shift();
    }

    trendChart.update('none'); // quiet update, no animation jank
  }

  function loadInitData(records) {
    // Clear existing
    chartLabels.length = 0;
    chartData.temp.length = 0;
    chartData.humidity.length = 0;
    chartData.pressure.length = 0;
    chartData.lux.length = 0;

    if (!Array.isArray(records)) return;

    // Sort chronologically
    records.sort(function (a, b) {
      return new Date(a.createdAt) - new Date(b.createdAt);
    });

    records.forEach(function (r) {
      chartLabels.push(r.createdAt);
      chartData.temp.push(r.temperature);
      chartData.humidity.push(r.humidity);
      chartData.pressure.push(r.pressure);
      chartData.lux.push(r.lux);
    });

    trendChart.update('none');

    // Set current-reading cards to the latest record
    if (records.length > 0) {
      var latest = records[records.length - 1];
      updateReadingCards(latest);
      updateAlertsBar(latest);
    }
  }

  /* ─── SSE ─── */
  var es = new EventSource('/api/weather/stream');

  es.addEventListener('init', function (e) {
    try {
      var records = JSON.parse(e.data);
      loadInitData(records);
    } catch (err) {
      console.warn('Failed to parse init event:', err);
    }
  });

  es.addEventListener('update', function (e) {
    try {
      var record = JSON.parse(e.data);
      pushChartPoint(record);
      updateReadingCards(record);
      updateAlertsBar(record);
    } catch (err) {
      console.warn('Failed to parse update event:', err);
    }
  });

  es.addEventListener('advisory', function (e) {
    try {
      var advisory = JSON.parse(e.data);
      updateAdvisoryCard(advisory);
      showToast(
        (advisory.riskLevel || 'LOW') + ' — ' + (advisory.summary || ''),
        advisory.riskLevel
      );

      // Prepend to history list if the History page has been loaded at least once
      if (currentPage > 0 && historyList.children.length > 0) {
        var card = buildHistoryCard(advisory);
        if (historyList.firstChild) {
          historyList.insertBefore(card, historyList.firstChild);
        } else {
          historyList.appendChild(card);
        }
      }
    } catch (err) {
      console.warn('Failed to parse advisory event:', err);
    }
  });

  es.onerror = function () {
    console.warn('SSE connection lost, will retry automatically');
  };

  /* ─── Advisory History ─── */
  function loadAdvisoryPage(page) {
    currentPage = page;

    fetch('/api/weather/advisories?page=' + page + '&size=20')
      .then(function (res) { return res.json(); })
      .then(function (result) {
        // Response shape: { code: 200, message: "success", data: { content: [...], totalPages, ... } }
        if (!result || !result.data) return;

        var pageData = result.data;
        var advisories = pageData.content || [];
        totalPages = pageData.totalPages || 0;

        if (page === 1) {
          historyList.innerHTML = '';
        }

        if (advisories.length === 0 && page === 1) {
          historyList.innerHTML = '<div class="history-card">' +
            '<div class="hc-summary" style="color:var(--text-secondary); font-style:italic">' +
            'No advisory records yet' +
            '</div></div>';
          loadMoreWrap.style.display = 'none';
          return;
        }

        advisories.forEach(function (adv) {
          var card = buildHistoryCard(adv);
          historyList.appendChild(card);
        });

        // Show/hide load more
        if (currentPage < totalPages) {
          loadMoreWrap.style.display = 'block';
          loadMoreBtn.disabled = false;
        } else {
          loadMoreWrap.style.display = 'none';
        }
      })
      .catch(function (err) {
        console.warn('Failed to load advisories:', err);
        if (page === 1) {
          historyList.innerHTML = '<div class="history-card">' +
            '<div class="hc-summary" style="color:var(--risk-extreme)">' +
            'Failed to load — check backend' +
            '</div></div>';
        }
      });
  }

  function buildHistoryCard(adv) {
    var risk = adv.riskLevel || 'LOW';
    var card = document.createElement('div');
    card.className = 'history-card risk-' + risk;

    var rec = adv.record || {};

    card.innerHTML =
      '<div class="hc-header">' +
        '<span class="risk-badge risk-' + risk + '">' + risk + '</span>' +
        '<span style="font-family:\'DM Mono\',monospace;font-size:10px;color:var(--text-muted)">' +
          fmtTimeFull(adv.createdAt) +
        '</span>' +
      '</div>' +
      '<div class="hc-summary">' + escHtml(adv.summary || '') + '</div>' +
      '<div class="hc-expanded">' +
        '<div class="hc-recommendation">' + escHtml(adv.recommendation || '') + '</div>' +
        '<div class="hc-readings">' +
          '<span>T: ' + fmtNum(rec.temperature, 1) + '&deg;C</span>' +
          '<span>H: ' + fmtNum(rec.humidity, 1) + '%</span>' +
          '<span>P: ' + fmtNum(rec.pressure, 1) + 'hPa</span>' +
          '<span>L: ' + fmtNum(rec.lux, 0) + 'lux</span>' +
        '</div>' +
      '</div>';

    card.addEventListener('click', function () {
      card.classList.toggle('expanded');
    });

    return card;
  }

  function escHtml(str) {
    if (!str) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }

  loadMoreBtn.addEventListener('click', function () {
    if (currentPage < totalPages) {
      loadMoreBtn.disabled = true;
      loadMoreBtn.textContent = 'Loading…';
      loadAdvisoryPage(currentPage + 1);
    }
  });

  /* ─── Trends Page ─── */
  function initTrendsPage() {
    if (!trendsChartReady) {
      createTrendsChart();
      trendsChartReady = true;
      loadTrendsFromPreset(activePreset);
    }
  }

  function createTrendsChart() {
    var ctx = $('#trendsChart').getContext('2d');
    trendsChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Temperature (°C)',
            data: [],
            borderColor: '#ff6b35',
            backgroundColor: 'rgba(255,107,53,0.08)',
            borderWidth: 2,
            pointRadius: 0,
            pointHitRadius: 8,
            tension: 0.3,
            yAxisID: 'y',
            spanGaps: false
          },
          {
            label: 'Humidity (%)',
            data: [],
            borderColor: '#4ecdc4',
            backgroundColor: 'rgba(78,205,196,0.08)',
            borderWidth: 2,
            pointRadius: 0,
            pointHitRadius: 8,
            tension: 0.3,
            yAxisID: 'y',
            spanGaps: false
          },
          {
            label: 'Pressure (hPa)',
            data: [],
            borderColor: '#f7dc6f',
            backgroundColor: 'rgba(247,220,111,0.08)',
            borderWidth: 2,
            pointRadius: 0,
            pointHitRadius: 8,
            tension: 0.3,
            yAxisID: 'y',
            spanGaps: false
          },
          {
            label: 'Light (lux)',
            data: [],
            borderColor: '#ffeaa7',
            backgroundColor: 'rgba(255,234,167,0.08)',
            borderWidth: 2,
            pointRadius: 0,
            pointHitRadius: 8,
            tension: 0.3,
            yAxisID: 'y1',
            spanGaps: false
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false
        },
        plugins: {
          legend: {
            position: 'top',
            align: 'start',
            labels: {
              boxWidth: 14,
              boxHeight: 2,
              padding: 16,
              color: '#9b8c7d',
              font: {
                family: "'DM Mono', monospace",
                size: 10
              },
              usePointStyle: false
            }
          },
          tooltip: {
            backgroundColor: '#242629',
            titleColor: '#e8d5c4',
            bodyColor: '#9b8c7d',
            borderColor: '#2e3035',
            borderWidth: 1,
            titleFont: {
              family: "'DM Mono', monospace",
              size: 11
            },
            bodyFont: {
              family: "'Space Mono', monospace",
              size: 11
            },
            callbacks: {
              title: function (items) {
                if (items.length === 0) return '';
                return fmtTimeFull(items[0].label);
              }
            }
          }
        },
        scales: {
          x: {
            display: true,
            ticks: {
              color: '#5d4f42',
              font: { family: "'DM Mono', monospace", size: 9 },
              maxTicksLimit: 10,
              callback: function (_val, index) {
                var label = trendsChart.data.labels[index];
                if (!label) return '';
                // shorter format for trends: Jan 13 08:00
                var d = new Date(label);
                return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) +
                  ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
              }
            },
            grid: {
              color: 'rgba(46,48,53,0.5)',
              drawBorder: false
            }
          },
          y: {
            type: 'linear',
            display: true,
            position: 'left',
            title: {
              display: true,
              text: '°C / % / hPa',
              color: '#9b8c7d',
              font: { family: "'DM Mono', monospace", size: 9 }
            },
            ticks: {
              color: '#5d4f42',
              font: { family: "'DM Mono', monospace", size: 9 }
            },
            grid: {
              color: 'rgba(46,48,53,0.5)',
              drawBorder: false
            }
          },
          y1: {
            type: 'logarithmic',
            display: true,
            position: 'right',
            title: {
              display: true,
              text: 'lux (log)',
              color: '#9b8c7d',
              font: { family: "'DM Mono', monospace", size: 9 }
            },
            ticks: {
              color: '#5d4f42',
              font: { family: "'DM Mono', monospace", size: 9 },
              callback: function (value) {
                if (value >= 1000) return (value / 1000).toFixed(0) + 'k';
                return value;
              }
            },
            grid: { display: false },
            min: 1
          }
        }
      }
    });
  }

  function loadTrendsFromPreset(preset) {
    activePreset = preset;

    // Update preset button states
    $$('.preset-btn[data-preset]').forEach(function (btn) {
      btn.classList.toggle('active', btn.dataset.preset === preset);
    });

    // Clear custom date inputs
    $('#trends-from').value = '';
    $('#trends-to').value = '';

    var now = new Date();
    var from;

    if (preset === '24h') {
      from = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    } else if (preset === '7d') {
      from = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    } else {
      from = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    }

    loadTrends(from.toISOString(), now.toISOString());
  }

  function loadTrends(fromISO, toISO) {
    fetch('/api/weather/records?from=' + encodeURIComponent(fromISO) + '&to=' + encodeURIComponent(toISO) + '&aggregation=auto')
      .then(function (res) { return res.json(); })
      .then(function (result) {
        if (!result || !result.data) return;
        renderTrendsData(result.data);
      })
      .catch(function (err) {
        console.warn('Failed to load trends:', err);
      });
  }

  function renderTrendsData(buckets) {
    if (!trendsChart || !Array.isArray(buckets)) return;

    // Clear and repopulate chart data
    var labels = [];
    var tempData = [];
    var humidData = [];
    var pressData = [];
    var luxData = [];

    buckets.forEach(function (b) {
      labels.push(b.bucket);
      tempData.push(b.temperature);
      humidData.push(b.humidity);
      pressData.push(b.pressure);
      luxData.push(b.lux);
    });

    trendsChart.data.labels = labels;
    trendsChart.data.datasets[0].data = tempData;
    trendsChart.data.datasets[1].data = humidData;
    trendsChart.data.datasets[2].data = pressData;
    trendsChart.data.datasets[3].data = luxData;
    trendsChart.update('none');

    // Update summary strip
    renderSummary(tempData, 'sum-temp', 'sum-temp-avg', '°C', 1);
    renderSummary(humidData, 'sum-humid', 'sum-humid-avg', '%', 1);
    renderSummary(pressData, 'sum-press', 'sum-press-avg', 'hPa', 1);
    renderSummary(luxData, 'sum-lux', 'sum-lux-avg', 'lux', 0);
  }

  function renderSummary(data, rangeId, avgId, unit, decimals) {
    var rangeEl = $('#' + rangeId);
    var avgEl = $('#' + avgId);

    if (!data || data.length === 0) {
      rangeEl.textContent = '-- – --';
      avgEl.textContent = 'Avg: --' + unit;
      return;
    }

    var filtered = data.filter(function (v) { return v !== null && v !== undefined; });
    if (filtered.length === 0) {
      rangeEl.textContent = '-- – --';
      avgEl.textContent = 'Avg: --' + unit;
      return;
    }

    var min = Math.min.apply(null, filtered);
    var max = Math.max.apply(null, filtered);
    var avg = filtered.reduce(function (a, b) { return a + b; }, 0) / filtered.length;

    rangeEl.textContent = min.toFixed(decimals) + ' – ' + max.toFixed(decimals);
    avgEl.textContent = 'Avg: ' + avg.toFixed(decimals) + unit;
  }

  /* ─── Trends Event Listeners ─── */

  // Preset buttons
  $$('.preset-btn[data-preset]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      loadTrendsFromPreset(this.dataset.preset);
    });
  });

  // Custom date apply
  $('#trends-apply').addEventListener('click', function () {
    var fromVal = $('#trends-from').value;
    var toVal = $('#trends-to').value;
    if (!fromVal || !toVal) return;

    // Deselect presets
    activePreset = null;
    $$('.preset-btn[data-preset]').forEach(function (btn) {
      btn.classList.remove('active');
    });

    loadTrends(new Date(fromVal).toISOString(), new Date(toVal).toISOString());
  });

})();
