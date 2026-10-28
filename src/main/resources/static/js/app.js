/* Frontend estático para el Portal de Reclamaciones.
 * Sin frameworks ni dependencias de compilación.
 */
(() => {
  'use strict';

  const API_BASE = '';
  const MIN_OBSERVATION = 20;
  const MIN_REASON = 10;

  const state = {
    token: sessionStorage.getItem('sura_jwt') || '',
    username: sessionStorage.getItem('sura_username') || '',
    role: sessionStorage.getItem('sura_role') || '',
    page: 0,
    totalPages: 1,
    totalCases: 0,
    currentCases: [],
    pendingFile: null,
    latestBatch: null,
    currentDetailCode: null,
    caseLoadId: 0
  };

  const $ = (id) => document.getElementById(id);
  const loginScreen = $('loginScreen');
  const appShell = $('appShell');

  const ALIASES = {
    total: ['totalCasos', 'total', 'cantidadTotal', 'totalRegistros', 'casosTotales'],
    automatic: ['automaticos', 'casosAutomaticos', 'totalAutomaticos', 'cantidadAutomaticos', 'aprobadosAutomaticos'],
    manual: ['manuales', 'casosManuales', 'totalManuales', 'cantidadManuales'],
    automation: ['porcentajeAutomatizacion', 'porcentajeAutomaticos', 'tasaAutomatizacion', 'porcentajeAutomatizacionPct'],
    toPay: ['valorAPagar', 'valorPendientePago', 'totalValorAPagar', 'montoAPagar', 'valorAprobado'],
    value: ['valorTotal', 'montoTotal', 'valor', 'totalValor'],
    state: ['porEstado', 'casosPorEstado', 'estados', 'distribucionEstados', 'conteoPorEstado'],
    priority: ['porPrioridad', 'casosPorPrioridad', 'prioridades', 'distribucionPrioridad', 'conteoPorPrioridad'],
    semaphore: ['porSemaforo', 'casosPorSemaforo', 'semaforos', 'distribucionSemaforo', 'conteoPorSemaforo']
  };

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[char]);
  }

  function normalizeKey(value) {
    return String(value ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }

  function getValue(obj, candidates, fallback = undefined) {
    if (!obj || typeof obj !== 'object') return fallback;

    const properties = Object.keys(obj);

    for (const candidate of candidates) {
      const found = properties.find(
        (key) => normalizeKey(key) === normalizeKey(candidate)
      );

      if (
        found !== undefined &&
        obj[found] !== null &&
        obj[found] !== undefined
      ) {
        return obj[found];
      }
    }

    return fallback;
  }

  function text(value, fallback = '—') {
    if (value === null || value === undefined || value === '') {
      return fallback;
    }

    if (typeof value === 'object') {
      return JSON.stringify(value);
    }

    return String(value);
  }

  function currency(value) {
    if (value === null || value === undefined || value === '') {
      return '—';
    }

    const numeric = typeof value === 'number'
      ? value
      : Number(
          String(value)
            .replace(/[^0-9,.-]/g, '')
            .replace(/\.(?=\d{3}(?:\D|$))/g, '')
            .replace(',', '.')
        );

    if (!Number.isFinite(numeric)) {
      return text(value);
    }

    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0
    }).format(numeric);
  }

  function prettyLabel(value) {
    return text(value)
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/(^|\s)\S/g, (match) => match.toUpperCase());
  }

  function isTrue(value) {
    return value === true ||
      ['TRUE', 'SI', 'SÍ', 'YES', '1'].includes(
        String(value ?? '').trim().toUpperCase()
      );
  }

  function decodeJwtPayload(token) {
    try {
      const segment = token
        .split('.')[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/');

      const padded = segment + '='.repeat((4 - segment.length % 4) % 4);

      return JSON.parse(
        decodeURIComponent(
          Array.from(
            atob(padded),
            (character) =>
              '%' + character.charCodeAt(0).toString(16).padStart(2, '0')
          ).join('')
        )
      );
    } catch (_) {
      return {};
    }
  }

  function deriveRole(loginResult, token, username) {
    const principal = getValue(
      loginResult,
      ['user', 'usuario', 'perfil'],
      {}
    ) || {};

    const fromResponse = getValue(
      loginResult,
      ['role', 'rol', 'authority', 'authorities'],
      getValue(
        principal,
        ['role', 'rol', 'authority', 'authorities'],
        ''
      )
    );

    const claims = decodeJwtPayload(token);
    const claim = getValue(
      claims,
      ['scope', 'scp', 'roles', 'authorities'],
      ''
    );

    const combined = `${
      Array.isArray(fromResponse) ? fromResponse.join(' ') : fromResponse
    } ${
      Array.isArray(claim) ? claim.join(' ') : claim
    } ${username}`.toUpperCase();

    if (combined.includes('ADMIN')) return 'ADMIN';
    if (combined.includes('ANALISTA')) return 'ANALISTA';

    return 'USUARIO';
  }

  function showToast(message, kind = '') {
    const region = $('toastRegion');
    if (!region) return;

    const toast = document.createElement('div');
    toast.className = `toast ${kind}`;
    toast.textContent = message;

    region.appendChild(toast);
    window.setTimeout(() => toast.remove(), 5500);
  }

  function setButtonBusy(button, busy, busyLabel = 'Procesando…') {
    if (!button) return;

    if (busy) {
      if (!button.dataset.originalText) {
        button.dataset.originalText = button.textContent;
      }

      button.textContent = busyLabel;
      button.disabled = true;
    } else {
      button.textContent =
        button.dataset.originalText || button.textContent;
      button.disabled = false;
      delete button.dataset.originalText;
    }
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});

    if (state.token) {
      headers.set('Authorization', `Bearer ${state.token}`);
    }

    const isFormData = options.body instanceof FormData;

    if (
      options.body !== undefined &&
      !isFormData &&
      !headers.has('Content-Type')
    ) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      credentials: 'same-origin'
    });

    if (response.status === 401 && state.token) {
      clearSession();
      throw new Error('Tu sesión expiró. Inicia sesión nuevamente.');
    }

    if (!response.ok) {
      let message = `La solicitud falló (HTTP ${response.status}).`;
      const contentType = response.headers.get('content-type') || '';

      try {
        if (contentType.includes('json')) {
          const body = await response.json();

          message = text(
            getValue(
              body,
              ['message', 'mensaje', 'error', 'detail', 'descripcion']
            ),
            message
          );
        } else {
          const bodyText = await response.text();

          if (bodyText && bodyText.length < 600) {
            message = bodyText;
          }
        }
      } catch (_) {
        // Conservamos el mensaje genérico.
      }

      throw new Error(message);
    }

    if (response.status === 204) return null;

    const contentType = response.headers.get('content-type') || '';

    if (
      contentType.includes('application/json') ||
      contentType.includes('+json')
    ) {
      return response.json();
    }

    return response;
  }

  function clearSession() {
    state.token = '';
    state.username = '';
    state.role = '';

    sessionStorage.removeItem('sura_jwt');
    sessionStorage.removeItem('sura_username');
    sessionStorage.removeItem('sura_role');

    loginScreen.classList.remove('hidden');
    appShell.classList.add('hidden');

    $('password').value = '';
    $('loginError').textContent = '';
  }

  function applyRoleVisibility() {
    document.querySelectorAll('.admin-only').forEach((element) => {
      element.classList.toggle('hidden', state.role !== 'ADMIN');
    });

    document.querySelectorAll('.admin-analyst').forEach((element) => {
      element.classList.toggle(
        'hidden',
        !['ADMIN', 'ANALISTA'].includes(state.role)
      );
    });

    $('currentUsername').textContent = state.username || 'Usuario';
    $('currentRole').textContent = state.role || 'USUARIO';

    $('userAvatar').textContent =
      (state.username || 'U').slice(0, 1).toUpperCase();

    if (state.role === 'ADMIN') {
      document.querySelector('[data-view="parameters"]')
        ?.classList.remove('hidden');
    }
  }

  function enterApplication() {
    loginScreen.classList.add('hidden');
    appShell.classList.remove('hidden');

    applyRoleVisibility();
    showView('dashboard');
    loadDashboard();
  }

  function showView(view) {
    const allowed = [
      'dashboard',
      'cases',
      'import',
      'batches',
      'parameters'
    ];

    if (!allowed.includes(view)) return;

    if (view === 'parameters' && state.role !== 'ADMIN') {
      showToast('Solo ADMIN puede consultar los parámetros.', 'error');
      return;
    }

    document.querySelectorAll('.view-section').forEach((section) => {
      section.classList.add('hidden');
    });

    $(`view-${view}`).classList.remove('hidden');

    document.querySelectorAll('.nav-item').forEach((button) => {
      button.classList.toggle('active', button.dataset.view === view);
    });

    const titles = {
      dashboard: 'Tablero',
      cases: 'Gestión de casos',
      import: 'Carga de archivo',
      batches: 'Lotes de pago',
      parameters: 'Parámetros'
    };

    $('pageTitle').textContent = titles[view];
    $('sidebar').classList.remove('open');

    if (view === 'cases') loadCases();
    if (view === 'parameters') loadParameters();
  }

  function firstArray(value, depth = 0) {
    if (Array.isArray(value)) return value;

    if (
      !value ||
      typeof value !== 'object' ||
      depth > 5
    ) {
      return [];
    }

    const keys = [
      'contenido',
      'content',
      'casos',
      'items',
      'data',
      'resultados',
      'results',
      'records',
      'elements',
      'parameters',
      'parametros',
      'resultado',
      'result',
      'payload',
      'page',
      'lista',
      'list'
    ];

    for (const key of keys) {
      const candidate = getValue(value, [key]);

      if (Array.isArray(candidate)) {
        return candidate;
      }

      if (candidate && typeof candidate === 'object') {
        const nested = firstArray(candidate, depth + 1);

        if (nested.length || Array.isArray(candidate)) {
          return nested;
        }
      }
    }

    return [];
  }

  function findNestedNumber(value, candidates, depth = 0) {
    if (!value || typeof value !== 'object' || depth > 5) {
      return null;
    }

    const direct = getValue(value, candidates);
    const number = countNumber(direct);

    if (number !== null) return number;

    for (const key of [
      'contenido',
      'content',
      'data',
      'resultado',
      'result',
      'payload',
      'page'
    ]) {
      const nested = getValue(value, [key]);

      if (nested && typeof nested === 'object') {
        const found = findNestedNumber(nested, candidates, depth + 1);

        if (found !== null) return found;
      }
    }

    return null;
  }

  function countNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }

    if (typeof value === 'string') {
      const parsed = Number(value.replace(/[^0-9.-]/g, ''));

      if (Number.isFinite(parsed)) return parsed;
    }

    return null;
  }

  function findCounts(data, candidates) {
    const found = getValue(data, candidates);

    if (found === undefined) return null;

    if (Array.isArray(found)) return found.length;

    if (typeof found === 'object') {
      if (Array.isArray(getValue(found, ['content', 'items', 'casos']))) {
        return firstArray(found).length;
      }

      return found;
    }

    return countNumber(found);
  }

  function countRows(value) {
    if (Array.isArray(value)) {
      return value.map((item) => ({
        label: text(
          getValue(
            item,
            [
              'estado',
              'prioridad',
              'semaforo',
              'nombre',
              'clave',
              'categoria',
              'label'
            ],
            'Sin categoría'
          )
        ),
        count: countNumber(
          getValue(
            item,
            ['cantidad', 'total', 'conteo', 'casos', 'count', 'valor'],
            0
          )
        ) ?? 0
      }));
    }

    if (value && typeof value === 'object') {
      return Object.entries(value).map(([label, number]) => ({
        label,
        count: countNumber(
          typeof number === 'object'
            ? getValue(number, ['cantidad', 'total', 'count', 'valor'], 0)
            : number
        ) ?? 0
      }));
    }

    return [];
  }

  function candidateNested(data, candidates) {
    const direct = getValue(data, candidates);

    if (direct !== undefined) return direct;

    for (const key of [
      'distribuciones',
      'distribucion',
      'estadisticas',
      'desglose'
    ]) {
      const nested = getValue(data, [key]);
      const result = getValue(nested, candidates);

      if (result !== undefined) return result;
    }

    return undefined;
  }

  function renderMetric(label, value, foot, icon) {
    return `
      <article class="metric-card">
        <span class="metric-label">${escapeHtml(label)}</span>
        <strong class="metric-value">${escapeHtml(value)}</strong>
        <span class="metric-foot">${escapeHtml(foot || icon || '')}</span>
      </article>
    `;
  }

  function renderDistribution(containerId, rows, kind = '') {
    const container = $(containerId);
    if (!container) return;

    if (!rows.length) {
      container.innerHTML =
        '<p class="muted">La respuesta no incluye datos de esta distribución.</p>';
      return;
    }

    const max = Math.max(1, ...rows.map((row) => row.count));

    if (kind === 'states') {
      container.innerHTML = rows
        .sort((a, b) => b.count - a.count)
        .map((row) => `
          <div class="distribution-row">
            <span class="distribution-label">${escapeHtml(prettyLabel(row.label))}</span>
            <div class="progress-track">
              <div
                class="progress-fill"
                style="width:${Math.max(0, Math.min(100, row.count / max * 100))}%">
              </div>
            </div>
            <strong class="distribution-count">${row.count}</strong>
          </div>
        `)
        .join('');
    } else if (kind === 'priority') {
      container.innerHTML = rows
        .sort((a, b) => b.count - a.count)
        .map((row) => `
          <div class="priority-item">
            <span class="priority-left">
              <span class="priority-dot ${escapeHtml(row.label.toLowerCase())}"></span>
              ${escapeHtml(prettyLabel(row.label))}
            </span>
            <strong class="priority-count">${row.count}</strong>
          </div>
        `)
        .join('');
    } else {
      container.innerHTML = rows
        .sort((a, b) => b.count - a.count)
        .map((row) => `
          <div class="semaphore-item">
            <span class="semaphore-left">
              <span class="semaphore-dot ${escapeHtml(row.label.toUpperCase())}"></span>
              ${escapeHtml(prettyLabel(row.label))}
            </span>
            <strong>${row.count}</strong>
          </div>
        `)
        .join('');
    }
  }

  function renderApprovalCard(titulo, resumen, estado, tipo) {
    const datos = resumen && typeof resumen === 'object'
      ? resumen
      : {};

    const numero = (valor) => {
      const resultado = Number(valor ?? 0);
      return Number.isFinite(resultado) ? resultado : 0;
    };

    const formatoNumero = (valor) => numero(valor).toLocaleString('es-CO', {
      maximumFractionDigits: 2
    });

    const cantidad = numero(datos.cantidad);
    const valorTotal = numero(datos.valorTotal);
    const valorPromedio = numero(datos.valorPromedio);
    const porcentaje = numero(datos.porcentajeDelTotal);
    const casosSinValor = numero(datos.casosSinValor);

    const prioridades = datos.casosPorPrioridad || {};
    const semaforos = datos.casosPorSemaforo || {};

    const prioridadAlta = numero(prioridades.ALTA);
    const prioridadMedia = numero(prioridades.MEDIA);
    const prioridadBaja = numero(prioridades.BAJA);
    const sinPrioridad = numero(prioridades.SIN_PRIORIDAD);

    const ansRojo = numero(semaforos.ROJO);
    const ansAmarillo = numero(semaforos.AMARILLO);
    const ansVerde = numero(semaforos.VERDE);
    const ansCumplido = numero(semaforos.CUMPLIDO);
    const ansSinSemaforo = numero(semaforos.SIN_SEMAFORO);

    return `
      <article class="approval-card approval-card--${tipo}">
        <div class="approval-card-heading">
          <div>
            <p class="approval-card-kicker">RESUMEN DE APROBACIONES</p>
            <h2 class="approval-card-title">${escapeHtml(titulo)}</h2>
          </div>
          <span class="approval-pill">
            ${tipo === 'automatic' ? 'AUTOMÁTICO' : 'ANALISTA'}
          </span>
        </div>

        <div class="approval-primary-metrics">
          <div class="approval-primary-item">
            <span class="approval-metric-label">Casos aprobados</span>
            <strong class="approval-count">${formatoNumero(cantidad)}</strong>
            <small>${formatoNumero(porcentaje)}% del total de casos registrados</small>
          </div>

          <div class="approval-primary-item">
            <span class="approval-metric-label">Sumatoria del valor reclamado</span>
            <strong class="approval-total">${escapeHtml(currency(valorTotal))}</strong>
            <small>Suma de los importes registrados</small>
          </div>
        </div>

        <div class="approval-details">
          <div class="approval-detail">
            <span>Valor promedio por caso con importe</span>
            <strong>${escapeHtml(currency(valorPromedio))}</strong>
          </div>
          <div class="approval-detail">
            <span>Prioridad alta</span>
            <strong>${formatoNumero(prioridadAlta)}</strong>
          </div>
          <div class="approval-detail">
            <span>Prioridad media</span>
            <strong>${formatoNumero(prioridadMedia)}</strong>
          </div>
          <div class="approval-detail">
            <span>Prioridad baja</span>
            <strong>${formatoNumero(prioridadBaja)}</strong>
          </div>
          <div class="approval-detail">
            <span>Sin prioridad</span>
            <strong>${formatoNumero(sinPrioridad)}</strong>
          </div>
          <div class="approval-detail">
            <span>ANS rojo / amarillo</span>
            <strong>${formatoNumero(ansRojo)} / ${formatoNumero(ansAmarillo)}</strong>
          </div>
          <div class="approval-detail">
            <span>ANS verde / cumplido</span>
            <strong>${formatoNumero(ansVerde)} / ${formatoNumero(ansCumplido)}</strong>
          </div>
          <div class="approval-detail">
            <span>Sin semáforo ANS</span>
            <strong>${formatoNumero(ansSinSemaforo)}</strong>
          </div>
          <div class="approval-detail">
            <span>Casos sin importe registrado</span>
            <strong>${formatoNumero(casosSinValor)}</strong>
          </div>
        </div>

        <div class="approval-card-actions">
          <span class="approval-state-caption">
            Estado: ${escapeHtml(estado)}
          </span>
          <button
            type="button"
            class="button button-primary approval-detail-button"
            data-open-approval="${escapeHtml(estado)}">
            Ver a detalle <span aria-hidden="true">→</span>
          </button>
        </div>
      </article>
    `;
  }

  async function loadDashboard() {
    $('dashboardAlert').classList.add('hidden');

    try {
      const data = await api('/api/reportes/resumen');

      const total = findCounts(
        data,
        ['totalCasos', 'total', 'cantidadTotal', 'totalRegistros', 'casosTotales']
      );

      const automatic = findCounts(
        data,
        ['automaticos', 'casosAutomaticos', 'totalAutomaticos', 'cantidadAutomaticos', 'aprobadosAutomaticos']
      );

      const manual = findCounts(
        data,
        ['manuales', 'casosManuales', 'totalManuales', 'cantidadManuales']
      );

      let automation = findCounts(
        data,
        ['porcentajeAutomatizacion', 'porcentajeAutomaticos', 'tasaAutomatizacion']
      );

      if (automation === null && automatic !== null && total) {
        automation = Math.round(automatic / total * 1000) / 10;
      }

      const toPay = findCounts(
        data,
        ['valorAPagar', 'valorPendientePago', 'totalValorAPagar', 'montoAPagar', 'valorAprobado']
      );

      $('metricsGrid').innerHTML = [
        renderMetric(
          'Total de casos',
          total === null ? '—' : total,
          'Casos registrados'
        ),
        renderMetric(
          'Automáticos',
          automatic === null ? '—' : automatic,
          total !== null && total
            ? `${Math.round(automatic / total * 100)}% del total`
            : 'Decisión automática'
        ),
        renderMetric(
          'Manuales',
          manual === null ? '—' : manual,
          'Requieren o requirieron revisión'
        ),
        renderMetric(
          'Automatización',
          automation === null ? '—' : `${automation}%`,
          toPay === null
            ? 'Porcentaje de automatización'
            : `Valor a pagar: ${currency(toPay)}`
        )
      ].join('');

      const approvalCards = $('approvalCards');

      if (approvalCards) {
        const resumenAutomatico = getValue(
          data,
          ['resumenAprobadosAutomaticos'],
          null
        );

        const resumenManual = getValue(
          data,
          ['resumenAprobadosManuales'],
          null
        );

        approvalCards.innerHTML = [
          renderApprovalCard(
            'Aprobados automáticos',
            resumenAutomatico,
            'APROBADO_AUTOMATICO',
            'automatic'
          ),
          renderApprovalCard(
            'Aprobados manuales',
            resumenManual,
            'APROBADO_ANALISTA',
            'manual'
          )
        ].join('');
      }

      const states = countRows(
        candidateNested(data, ALIASES.state)
      );

      const priorities = countRows(
        candidateNested(data, ALIASES.priority)
      );

      const semaphores = countRows(
        candidateNested(data, ALIASES.semaphore)
      );

      renderDistribution('stateDistribution', states, 'states');
      renderDistribution('priorityDistribution', priorities, 'priority');
      renderDistribution('semaphoreDistribution', semaphores, 'semaphore');

      if (!states.length || !priorities.length || !semaphores.length) {
        $('dashboardAlert').textContent =
          'El resumen se recibió correctamente. Algunas distribuciones no se pudieron mapear automáticamente; puedes verificar sus nombres en la respuesta de /api/reportes/resumen.';

        $('dashboardAlert').classList.remove('hidden');
      }
    } catch (error) {
      $('dashboardAlert').textContent =
        `No se pudo cargar el resumen: ${error.message}`;

      $('dashboardAlert').classList.remove('hidden');

      $('metricsGrid').innerHTML = [
        'Total de casos',
        'Automáticos',
        'Manuales',
        'Automatización'
      ]
        .map((label) => renderMetric(label, '—', 'No disponible'))
        .join('');

      const approvalCards = $('approvalCards');

      if (approvalCards) {
        approvalCards.innerHTML = `
          <div class="inline-alert">
            No se pudieron cargar los indicadores de aprobaciones.
            Comprueba la conexión con el backend e intenta actualizar el tablero.
          </div>
        `;
      }
    }
  }

  function caseCode(item) {
    return text(
      getValue(
        item,
        ['codigoCaso', 'identificador', 'idCaso', 'codigo', 'caseId', 'id'],
        ''
      )
    );
  }

  function caseState(item) {
    return text(getValue(item, ['estadoActual', 'estado', 'status'], ''));
  }

  function caseDecision(item) {
    return text(
      getValue(item, ['decision', 'tipoDecision', 'clasificacion'], '')
    );
  }

  function casePriority(item) {
    return text(getValue(item, ['prioridad'], 'SIN_PRIORIDAD'));
  }

  function caseSemaphore(item) {
    return text(
      getValue(item, ['semaforoANS', 'semaforo', 'semaforoAns'], '')
    );
  }

  function caseValue(item) {
    return getValue(
      item,
      ['valorReclamado', 'valor', 'monto', 'valorCaso', 'importe'],
      ''
    );
  }

  function caseName(item) {
    return text(
      getValue(item, ['nombreAfiliado', 'afiliado', 'nombre', 'beneficiario'], 'Afiliado')
    );
  }

  function caseHasMonetaryError(item) {
    const flag = getValue(item, ['errorMonetario'], undefined);

    if (flag !== undefined) {
      return isTrue(flag);
    }

    const original = getValue(
      item,
      ['valorReclamadoOriginal'],
      undefined
    );

    const amount = getValue(
      item,
      ['valorReclamado', 'valor', 'monto'],
      undefined
    );

    return original !== undefined &&
      original !== null &&
      (amount === undefined || amount === null);
  }

  function caseRules(item) {
    const raw = getValue(
      item,
      ['reglasIncumplidas', 'reglasFallidas', 'reglasNoCumplidas', 'reglas'],
      []
    );

    if (Array.isArray(raw)) {
      return raw
        .map((entry) =>
          typeof entry === 'object' && entry !== null
            ? text(
                getValue(
                  entry,
                  ['codigoRegla', 'codigo', 'regla', 'id'],
                  JSON.stringify(entry)
                )
              )
            : String(entry)
        )
        .join(', ');
    }

    return text(raw, '—');
  }

  function getListResponse(data) {
    const rows = firstArray(data);

    const total = findNestedNumber(data, [
      'totalElementos',
      'totalElements',
      'totalRegistros',
      'total',
      'cantidadTotal',
      'totalCasos',
      'totalCount',
      'count'
    ]) ?? rows.length;

    const pageSize = Math.max(
      1,
      Number(
        getValue(
          data,
          ['tamano', 'size', 'pageSize'],
          $('pageSize')?.value || 20
        )
      ) || 20
    );

    const pages = findNestedNumber(
      data,
      ['totalPaginas', 'totalPages', 'paginas', 'pages']
    ) ?? Math.max(1, Math.ceil(total / pageSize));

    return { rows, total, pages, pageSize };
  }

  function caseRow(item) {
    const code = caseCode(item);
    const decision = caseDecision(item).toUpperCase();
    const priority = casePriority(item).toUpperCase();
    const semaphore = caseSemaphore(item).toUpperCase();
    const errorMonetario = caseHasMonetaryError(item);

    return `
      <tr>
        <td>
          <div class="case-cell">
            <strong>${escapeHtml(code || 'Sin identificador')}</strong>
            <small>${escapeHtml(caseName(item))}</small>
            <small>${escapeHtml(
              getValue(
                item,
                ['documentoEnmascarado', 'documentoMask', 'documentoAfiliado'],
                ''
              )
            )}</small>
            ${
              errorMonetario
                ? '<small class="monetary-error-label">⚠ Error monetario · Requiere revisión</small>'
                : ''
            }
          </div>
        </td>

        <td>
          <span class="decision-tag ${decision.includes('AUTO') ? 'automatic' : 'manual'}">
            ${escapeHtml(prettyLabel(decision || '—'))}
          </span>
        </td>

        <td><strong>${escapeHtml(currency(caseValue(item)))}</strong></td>

        <td>
          <span class="priority-tag ${escapeHtml(priority)}">
            ${escapeHtml(prettyLabel(priority))}
          </span>
        </td>

        <td>
          <span class="state-tag">
            ${escapeHtml(prettyLabel(caseState(item)))}
          </span>
        </td>

        <td>
          <span class="semaphore-tag ${escapeHtml(semaphore)}">
            ${escapeHtml(prettyLabel(semaphore))}
          </span>
        </td>

        <td>
          <button
            class="table-action"
            data-case-detail="${escapeHtml(code)}">
            Ver detalle →
          </button>
        </td>
      </tr>
    `;
  }

  function normalizeSearch(value) {
    return String(value ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es-CO')
      .trim();
  }

  async function loadCases() {
    const loadId = (state.caseLoadId || 0) + 1;
    state.caseLoadId = loadId;

    const searchText = $('caseSearch').value.trim();
    const search = normalizeSearch(searchText);
    const requestedSize = Number($('pageSize').value || 20);

    const filters = {
      estado: $('filterEstado').value,
      decision: $('filterDecision').value,
      prioridad: $('filterPrioridad').value,
      regla: $('filterRegla').value.trim(),
      semaforo: $('filterSemaforo').value
    };

    const makeParams = (page, size) => {
      const params = new URLSearchParams();

      Object.entries({
        ...filters,
        pagina: page,
        tamano: size
      }).forEach(([key, value]) => {
        if (value !== '' && value !== null && value !== undefined) {
          params.set(key, value);
        }
      });

      return params.toString();
    };

    $('casesTableBody').innerHTML =
      '<tr><td colspan="7" class="table-empty">Cargando casos…</td></tr>';

    $('caseCount').textContent = search
      ? 'Buscando en todos los resultados…'
      : 'Consultando casos…';

    try {
      if (!search) {
        const data = await api(
          `/api/casos?${makeParams(state.page, requestedSize)}`
        );

        if (loadId !== state.caseLoadId) return;

        const response = getListResponse(data);
        const rows = response.rows;
        const total = response.total;
        const pages = Math.max(1, response.pages);

        if (state.page >= pages && state.page > 0) {
          state.page = pages - 1;
          return loadCases();
        }

        state.currentCases = rows;
        state.totalCases = total;
        state.totalPages = pages;

        $('caseCount').textContent = `${total} caso(s) en el resultado`;
        $('pageInfo').textContent = `Página ${state.page + 1} de ${pages}`;

        $('previousPage').disabled = state.page <= 0;
        $('nextPage').disabled = state.page + 1 >= pages;

        if (rows.length) {
          $('casesTableBody').innerHTML = rows.map(caseRow).join('');
        } else if (total === 0) {
          $('casesTableBody').innerHTML =
            '<tr><td colspan="7" class="table-empty">No hay casos que coincidan con los filtros.</td></tr>';
        } else {
          $('casesTableBody').innerHTML =
            '<tr><td colspan="7" class="table-empty">La API informó resultados, pero no se pudo interpretar la lista. Revisa la respuesta de /api/casos.</td></tr>';
        }

        return;
      }

      // Búsqueda textual global mediante las páginas disponibles del backend.
      const searchFetchSize = Math.min(
        50,
        Math.max(1, requestedSize || 20)
      );

      const firstData = await api(
        `/api/casos?${makeParams(0, searchFetchSize)}`
      );

      if (loadId !== state.caseLoadId) return;

      const firstPage = getListResponse(firstData);
      const allRows = [...firstPage.rows];

      const totalServer = Math.max(
        firstPage.total,
        allRows.length
      );

      const metadataPageSize = Math.max(
        1,
        firstPage.pageSize || searchFetchSize
      );

      const effectivePageSize =
        totalServer > allRows.length && allRows.length > 0
          ? Math.min(metadataPageSize, allRows.length)
          : metadataPageSize;

      const pagesServer = allRows.length >= totalServer
        ? 1
        : Math.max(
            1,
            firstPage.pages || 1,
            Math.ceil(totalServer / effectivePageSize)
          );

      const batchSize = 4;

      for (
        let page = 1;
        page < pagesServer;
        page += batchSize
      ) {
        const pageNumbers = [];

        for (
          let next = page;
          next < Math.min(page + batchSize, pagesServer);
          next++
        ) {
          pageNumbers.push(next);
        }

        const pageResponses = await Promise.all(
          pageNumbers.map(async (pageNumber) => {
            const pageData = await api(
              `/api/casos?${makeParams(pageNumber, effectivePageSize)}`
            );

            return getListResponse(pageData).rows;
          })
        );

        pageResponses.forEach((pageRows) => allRows.push(...pageRows));

        if (loadId !== state.caseLoadId) return;
      }

      const uniqueRows = [];
      const seenCodes = new Set();

      for (const item of allRows) {
        const code = caseCode(item) || String(getValue(item, ['id'], ''));

        if (!code || !seenCodes.has(code)) {
          uniqueRows.push(item);

          if (code) seenCodes.add(code);
        }
      }

      const matching = uniqueRows.filter((item) => {
        const searchable = [
          caseCode(item),
          caseName(item),
          getValue(item, ['documentoEnmascarado', 'documentoMask'], ''),
          caseDecision(item),
          caseState(item),
          casePriority(item),
          caseSemaphore(item),
          caseRules(item),
          getValue(item, ['motivo', 'motivoNegocio', 'observaciones'], ''),
          caseValue(item),
          caseHasMonetaryError(item) ? 'error monetario revisión' : ''
        ]
          .map(normalizeSearch)
          .join(' ');

        return searchable.includes(search);
      });

      const total = matching.length;
      const pages = Math.max(
        1,
        Math.ceil(total / requestedSize)
      );

      if (state.page >= pages) {
        state.page = pages - 1;
      }

      const start = state.page * requestedSize;
      const rows = matching.slice(start, start + requestedSize);

      if (loadId !== state.caseLoadId) return;

      state.currentCases = rows;
      state.totalCases = total;
      state.totalPages = pages;

      $('caseCount').textContent =
        `${total} caso(s) en el resultado · búsqueda global: “${searchText}”`;

      $('pageInfo').textContent =
        `Página ${state.page + 1} de ${pages}`;

      $('previousPage').disabled = state.page <= 0;
      $('nextPage').disabled = state.page + 1 >= pages;

      if (rows.length) {
        $('casesTableBody').innerHTML = rows.map(caseRow).join('');
      } else {
        $('casesTableBody').innerHTML =
          '<tr><td colspan="7" class="table-empty">No hay casos que coincidan con los filtros y la búsqueda.</td></tr>';
      }
    } catch (error) {
      if (loadId !== state.caseLoadId) return;

      $('caseCount').textContent =
        'No fue posible consultar el listado';

      $('pageInfo').textContent = 'Página 1 de 1';

      $('casesTableBody').innerHTML = `
        <tr>
          <td colspan="7" class="table-empty">
            ${escapeHtml(error.message)}
          </td>
        </tr>
      `;
    }
  }

  function detailFields(detail) {
    const fields = [
      ['Identificador', ['codigoCaso', 'identificador', 'idCaso', 'codigo', 'id']],
      ['Estado actual', ['estadoActual', 'estado']],
      ['Decisión', ['decision']],
      ['Afiliado', ['nombreAfiliado', 'afiliado', 'nombre']],
      ['Documento', ['documento', 'numeroDocumento']],
      ['Documento enmascarado', ['documentoEnmascarado']],
      ['Póliza', ['poliza', 'numeroPoliza']],
      ['Valor reclamado', ['valorReclamado', 'valor', 'monto']],
      ['Prioridad', ['prioridad']],
      ['Fecha de radicación', ['fechaRadicacion', 'fechaRadicado']],
      ['Fecha límite ANS', ['fechaLimiteANS', 'fechaLimiteAns', 'fechaLimite']],
      ['Semáforo ANS', ['semaforoANS', 'semaforo']],
      ['Analista asignado', ['analistaAsignado', 'usuarioAsignado', 'asignadoA']]
    ];

    return fields
      .filter(([, keys]) => getValue(detail, keys) !== undefined)
      .map(([label, keys]) => {
        const value = getValue(detail, keys);

        const display =
          keys.some((key) => [
            'valorReclamado',
            'valor',
            'monto'
          ].includes(key))
            ? currency(value)
            : text(value);

        return `
          <div class="detail-field">
            <span>${escapeHtml(label)}</span>
            <strong>${escapeHtml(display)}</strong>
          </div>
        `;
      })
      .join('');
  }

  /**
   * Muestra la advertencia monetaria devuelta por ConsultaCasosService.
   * Presenta el texto original para facilitar la revisión del dato.
   */
  function renderMonetaryError(detail) {
    const flag = getValue(detail, ['errorMonetario'], undefined);

    if (flag !== undefined && !isTrue(flag)) {
      return '';
    }

    const original = getValue(
      detail,
      ['valorReclamadoOriginal', 'claimAmountRaw'],
      undefined
    );

    const amount = getValue(
      detail,
      ['valorReclamado'],
      undefined
    );

    const inferredError =
      flag === undefined &&
      original !== undefined &&
      (amount === undefined || amount === null);

    if (!isTrue(flag) && !inferredError) {
      return '';
    }

    const explanation = getValue(
      detail,
      ['detalleErrorMonetario'],
      original === undefined || String(original).trim() === ''
        ? 'El importe está vacío en el archivo de origen.'
        : 'El importe original no se pudo convertir a un valor monetario válido.'
    );

    const originalDisplay =
      original === undefined || original === null || String(original).trim() === ''
        ? 'El archivo no contenía un importe.'
        : String(original);

    return `
      <section class="detail-section">
        <div class="notice note-warning monetary-warning">
          <h3>⚠ Error en el importe reclamado</h3>

          <p>${escapeHtml(text(explanation))}</p>

          <div class="detail-field">
            <span>Valor original del Excel</span>
            <strong>${escapeHtml(originalDisplay)}</strong>
          </div>

          <p>
            El caso se conserva para revisión manual. Verifica el importe
            antes de tomar una decisión sobre la reclamación.
          </p>
        </div>
      </section>
    `;
  }

  /**
   * Muestra el campo Observaciones que fue importado desde el Excel.
   */
  function renderObservations(detail) {
    const observations = getValue(
      detail,
      ['observaciones', 'observacion', 'observations'],
      ''
    );

    const content =
      observations === null ||
      observations === undefined ||
      String(observations).trim() === ''
        ? 'No se registraron observaciones en el archivo de origen.'
        : String(observations);

    return `
      <section class="detail-section">
        <h3>Observaciones del Excel</h3>
        <div class="rule-item observation-content">
          <p>${escapeHtml(content)}</p>
        </div>
      </section>
    `;
  }

  /**
   * Convierte un valor de evidencia a texto legible.
   * Admite valores simples, listas y objetos anidados.
   */
  function formatEvidenceValue(value) {
    if (value === null || value === undefined || value === '') {
      return 'Sin dato';
    }

    if (typeof value === 'boolean') {
      return value ? 'Sí' : 'No';
    }

    if (Array.isArray(value)) {
      if (!value.length) {
        return 'Lista vacía';
      }

      return value
        .map((item) => formatEvidenceValue(item))
        .join(', ');
    }

    if (typeof value === 'object') {
      const entries = Object.entries(value);

      if (!entries.length) {
        return 'Sin datos';
      }

      return entries
        .map(([key, item]) => {
          return `${prettyLabel(key)}: ${formatEvidenceValue(item)}`;
        })
        .join('; ');
    }

    return String(value);
  }

  /**
   * Presenta evidencia registrada por el motor de reglas.
   */
  function renderRuleEvidence(evidence) {
    if (evidence === null || evidence === undefined) {
      return `
        <p class="muted rule-evidence">
          No se recibió evidencia para esta regla.
        </p>
      `;
    }

    if (typeof evidence !== 'object') {
      return `
        <p class="rule-evidence">
          <strong>Evidencia:</strong>
          ${escapeHtml(formatEvidenceValue(evidence))}
        </p>
      `;
    }

    const entries = Array.isArray(evidence)
      ? evidence.map((value, index) => [`Elemento ${index + 1}`, value])
      : Object.entries(evidence);

    if (!entries.length) {
      return `
        <p class="muted rule-evidence">
          No se registró evidencia estructurada para esta regla.
        </p>
      `;
    }

    return `
      <div class="rule-evidence">
        <strong>Evidencia registrada</strong>
        <dl>
          ${entries.map(([key, value]) => `
            <div>
              <dt>${escapeHtml(prettyLabel(key))}</dt>
              <dd>${escapeHtml(formatEvidenceValue(value))}</dd>
            </div>
          `).join('')}
        </dl>
      </div>
    `;
  }

  /**
   * Determina si una regla pasó.
   * Las reglas aprobadas se excluyen de la presentación del detalle.
   */
  function rulePassed(rule) {
    const value = getValue(
      rule,
      ['cumple', 'passed', 'aprobada'],
      undefined
    );

    if (value === true) return true;
    if (value === false) return false;

    const status = String(
      getValue(rule, ['resultado', 'estado', 'status'], value ?? '')
    ).trim().toUpperCase();

    if (
      ['TRUE', 'CUMPLE', 'APROBADA', 'APROBADO', 'PASS', 'PASSED'].includes(status)
    ) {
      return true;
    }

    if (
      ['FALSE', 'INCUMPLE', 'RECHAZADA', 'RECHAZADO', 'FAIL', 'FAILED'].includes(status)
    ) {
      return false;
    }

    return false;
  }

  /**
   * Renderiza exclusivamente las reglas incumplidas.
   *
   * Primero intenta utilizar el detalle de "reglas". Si está vacío,
   * recurre a "reglasIncumplidas", que puede contener solo códigos.
   */
  function renderRules(detail) {
    const detailedRules = getValue(
      detail,
      [
        'reglas',
        'detalleReglas',
        'reglasDetalle',
        'reglasEvaluadas',
        'resultadosReglas'
      ],
      undefined
    );

    let rules = Array.isArray(detailedRules) && detailedRules.length
      ? detailedRules
      : getValue(
          detail,
          [
            'reglasIncumplidas',
            'reglasFallidas',
            'reglasNoCumplidas'
          ],
          []
        );

    if (!Array.isArray(rules)) {
      rules = [];
    }

    // Defensa adicional: si recibimos todas las reglas, no mostramos las aprobadas.
    rules = rules.filter((rule) => {
      if (typeof rule !== 'object' || rule === null) {
        return true;
      }

      return !rulePassed(rule);
    });

    if (!rules.length) {
      return `
        <p class="muted">
          No hay reglas incumplidas registradas para esta evaluación.
        </p>
      `;
    }

    return rules.map((rule) => {
      // Algunas respuestas contienen directamente códigos de reglas.
      if (typeof rule !== 'object' || rule === null) {
        return `
          <div class="rule-item">
            <strong>${escapeHtml(text(rule))}</strong>
            <p><strong>Resultado:</strong> Incumple</p>
          </div>
        `;
      }

      const code = getValue(
        rule,
        ['codigo', 'codigoRegla', 'id', 'regla'],
        'Regla'
      );

      const name = getValue(
        rule,
        ['nombreRegla', 'nombre', 'titulo'],
        ''
      );

      const reason = getValue(
        rule,
        [
          'explicacion',
          'businessExplanation',
          'motivo',
          'detalle',
          'mensaje',
          'observacion'
        ],
        ''
      );

      const evidence = getValue(
        rule,
        ['evidencia', 'evidence'],
        null
      );

      const title = name
        ? `${text(code)} · ${text(name)}`
        : text(code);

      return `
        <div class="rule-item">
          <strong>${escapeHtml(title)}</strong>

          <p>
            <strong>Resultado:</strong>
            Incumple
          </p>

          <p>
            <strong>Explicación:</strong>
            ${escapeHtml(text(reason, 'No se registró una explicación.'))}
          </p>

          ${renderRuleEvidence(evidence)}
        </div>
      `;
    }).join('');
  }

  function renderTracking(tracking) {
    const historial = getValue(
      tracking,
      ['historial', 'eventos', 'tracking', 'history'],
      undefined
    );

    const rows = Array.isArray(tracking)
      ? tracking
      : (
          Array.isArray(historial)
            ? historial
            : firstArray(tracking)
        );

    if (!rows.length) {
      return '<p class="muted">No se recibieron eventos de tracking.</p>';
    }

    return rows.map((item) => {
      if (typeof item !== 'object' || item === null) {
        return `
          <div class="tracking-item">
            <div class="tracking-rail"><span></span></div>
            <div class="tracking-content">
              <strong>${escapeHtml(text(item))}</strong>
            </div>
          </div>
        `;
      }

      const status = getValue(
        item,
        ['estadoNuevo', 'estado', 'estadoActual', 'nuevoEstado'],
        'Evento'
      );

      const date = getValue(
        item,
        ['fechaHora', 'fechaCambio', 'timestamp', 'fecha', 'createdAt'],
        ''
      );

      const user = getValue(
        item,
        ['usuario', 'usuarioCambio', 'actor'],
        ''
      );

      const comment = getValue(
        item,
        ['comentario', 'observacion', 'mensaje'],
        ''
      );

      return `
        <div class="tracking-item">
          <div class="tracking-rail"><span></span></div>
          <div class="tracking-content">
            <strong>${escapeHtml(prettyLabel(status))}</strong>
            <p>${escapeHtml(text(date, ''))}${user ? ` · ${escapeHtml(text(user))}` : ''}</p>
            ${comment ? `<p>${escapeHtml(text(comment))}</p>` : ''}
          </div>
        </div>
      `;
    }).join('');
  }

  function caseStateFromDetail(detail) {
    return text(
      getValue(
        detail,
        ['estadoActual', 'estado', 'status'],
        ''
      )
    );
  }

  function detailActionButtons(detail) {
    const code = caseCode(detail) || state.currentDetailCode;
    const currentState = caseStateFromDetail(detail).toUpperCase();

    let html = '';

    if (
      state.role === 'ANALISTA' &&
      currentState === 'EN_BANDEJA'
    ) {
      html += `
        <button
          class="button button-primary"
          data-take-case="${escapeHtml(code)}">
          Tomar caso
        </button>
      `;
    }

    if (
      state.role === 'ANALISTA' &&
      currentState === 'EN_REVISION'
    ) {
      html += `
        <button
          class="button button-success"
          data-decision="APROBAR"
          data-code="${escapeHtml(code)}">
          Aprobar
        </button>

        <button
          class="button button-danger"
          data-decision="OBJETAR"
          data-code="${escapeHtml(code)}">
          Objetar
        </button>
      `;
    }

    return html ? `<div class="detail-actions">${html}</div>` : '';
  }

  async function openCaseDetail(code) {
    if (!code) return;

    state.currentDetailCode = code;

    $('caseModalTitle').textContent = code;
    $('caseModalSubtitle').textContent =
      'Consultando datos y trazabilidad…';

    $('caseModalBody').innerHTML =
      '<p class="muted">Cargando detalle del caso y tracking…</p>';

    $('caseModal').classList.remove('hidden');

    try {
      const [detail, tracking] = await Promise.all([
        api(`/api/casos/${encodeURIComponent(code)}`),
        api(`/api/casos/${encodeURIComponent(code)}/tracking`)
          .catch((error) => ({ _error: error.message }))
      ]);

      const realDetail = detail?.data || detail?.caso || detail;
      const trackError = tracking && tracking._error;

      $('caseModalSubtitle').textContent =
        `${prettyLabel(caseStateFromDetail(realDetail))} · ${prettyLabel(caseDecision(realDetail))}`;

      const motive = getValue(
        realDetail,
        ['motivo', 'motivoDecision', 'explicacion'],
        ''
      );

      $('caseModalBody').innerHTML = `
        <div class="detail-grid">
          ${detailFields(realDetail)}
        </div>

        ${renderMonetaryError(realDetail)}

        ${renderObservations(realDetail)}${renderRugiaAnalysis(realDetail)}

        <section class="detail-section">
          <h3>Reglas incumplidas</h3>
          <div>${renderRules(realDetail)}</div>
        </section>

        ${motive ? `
          <section class="detail-section">
            <h3>Motivo de negocio</h3>
            <div class="rule-item">
              <p>${escapeHtml(text(motive))}</p>
            </div>
          </section>
        ` : ''}

        <section class="detail-section">
          <h3>Historial de estados</h3>
          ${
            trackError
              ? `<p class="muted">No se pudo cargar el tracking: ${escapeHtml(trackError)}</p>`
              : renderTracking(tracking)
          }
        </section>

        ${detailActionButtons(realDetail)}
      `;
    } catch (error) {
      $('caseModalBody').innerHTML = `
        <div class="notice note-warning">
          No se pudo cargar el detalle: ${escapeHtml(error.message)}
        </div>
      `;
    }
  }

  async function takeCase(code) {
    try {
      await api(
        `/api/casos/${encodeURIComponent(code)}/tomar`,
        { method: 'POST' }
      );

      showToast(`Caso ${code} tomado correctamente.`, 'success');

      await loadCases();
      await openCaseDetail(code);
    } catch (error) {
      showToast(error.message, 'error');
    }
  }

  function openDecisionModal(code, action) {
    $('decisionCaseCode').value = code;
    $('decisionAction').value = action;

    $('decisionModalTitle').textContent =
      action === 'APROBAR'
        ? 'Aprobar reclamación'
        : 'Objetar reclamación';

    $('decisionModalSubtitle').textContent = `Caso ${code}`;
    $('decisionObservation').value = '';
    $('observationCount').textContent = '0';

    $('submitDecisionButton').textContent =
      action === 'APROBAR'
        ? 'Confirmar aprobación'
        : 'Confirmar objeción';

    $('decisionModal').classList.remove('hidden');
    $('decisionObservation').focus();
  }

  async function submitDecision(event) {
    event.preventDefault();

    const code = $('decisionCaseCode').value;
    const action = $('decisionAction').value;
    const observacion = $('decisionObservation').value.trim();

    if (observacion.length < MIN_OBSERVATION) {
      showToast(
        `La observación debe tener al menos ${MIN_OBSERVATION} caracteres.`,
        'error'
      );
      return;
    }

    const button = $('submitDecisionButton');
    setButtonBusy(button, true, 'Guardando…');

    try {
      await api(
        `/api/casos/${encodeURIComponent(code)}/estado`,
        {
          method: 'PATCH',
          body: JSON.stringify({ accion: action, observacion })
        }
      );

      $('decisionModal').classList.add('hidden');
      $('caseModal').classList.add('hidden');

      showToast(
        `Decisión ${action.toLowerCase()} registrada para ${code}.`,
        'success'
      );

      await loadCases();
      await loadDashboard();
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function runEvaluation() {
    const button = $('evaluateButton');

    if (!window.confirm(
      '¿Deseas reevaluar los casos elegibles? Los casos tomados por analistas deben conservar su estado.'
    )) {
      return;
    }

    setButtonBusy(button, true, 'Reevaluando…');

    try {
      const result = await api(
        '/api/casos/evaluar',
        { method: 'POST' }
      );

      showToast(
        'La reevaluación terminó. Revisa el resumen devuelto por el servidor.',
        'success'
      );

      $('dashboardAlert').textContent =
        `Resultado de reevaluación: ${JSON.stringify(result)}`;

      $('dashboardAlert').classList.remove('hidden');

      await loadCases();
      await loadDashboard();
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  function showUploadResult(data, kind = 'success') {
    const box = $('uploadResult');
    if (!box) return;

    box.className = `result-box ${kind}`;
    box.textContent = typeof data === 'string'
      ? data
      : JSON.stringify(data, null, 2);

    box.classList.remove('hidden');
  }

  /**
   * Muestra el resultado de la importación en un resumen legible.
   *
   * Usa los campos de ResultadoImportacionExcel:
   * exitoso, loteId, estado, totalFilas, importadas, filasConError,
   * casosConErrorMonetario, mensaje y errores.
   */
  function showImportResult(data) {
    const box = $('uploadResult');
    if (!box) return;

    const nested = getValue(data, ['data', 'resultado'], undefined);
    const result = nested && typeof nested === 'object'
      ? nested
      : data;

    const exitoso = getValue(result, ['exitoso', 'success'], true) !== false;
    const loteId = getValue(result, ['loteId', 'idLote'], 'No informado');
    const estado = getValue(result, ['estado', 'status'], 'No informado');
    const total = getValue(result, ['totalFilas', 'totalRows'], 'No informado');
    const importadas = getValue(result, ['importadas', 'casosImportados'], 'No informado');
    const incidencias = getValue(
      result,
      ['filasConError', 'incidencias', 'totalErrores'],
      0
    );
    const erroresMonetarios = getValue(
      result,
      ['casosConErrorMonetario', 'erroresMonetarios'],
      0
    );

    const mensaje = getValue(
      result,
      ['mensaje', 'message'],
      exitoso
        ? 'El servidor terminó el proceso de importación.'
        : 'La importación no se completó.'
    );

    const erroresRaw = getValue(
      result,
      ['errores', 'mensajes', 'detallesErrores'],
      []
    );

    const errores = Array.isArray(erroresRaw)
      ? erroresRaw
      : (
          erroresRaw && typeof erroresRaw === 'object'
            ? Object.values(erroresRaw).map((entry) => text(entry))
            : erroresRaw
              ? [String(erroresRaw)]
              : []
        );

    const tieneIncidencias =
      Number(incidencias || 0) > 0 ||
      Number(erroresMonetarios || 0) > 0 ||
      errores.length > 0 ||
      String(estado).toUpperCase().includes('ERROR');

    box.className = `result-box ${exitoso ? 'success' : 'error'}`;

    box.innerHTML = `
      <section class="import-summary">
        <h3>${exitoso ? 'Resultado de la importación' : 'Importación no completada'}</h3>

        <p>${escapeHtml(text(mensaje))}</p>

        <div class="detail-grid">
          <div class="detail-field">
            <span>ID del lote</span>
            <strong>${escapeHtml(text(loteId))}</strong>
          </div>

          <div class="detail-field">
            <span>Estado</span>
            <strong>${escapeHtml(prettyLabel(estado))}</strong>
          </div>

          <div class="detail-field">
            <span>Filas leídas</span>
            <strong>${escapeHtml(text(total))}</strong>
          </div>

          <div class="detail-field">
            <span>Casos importados</span>
            <strong>${escapeHtml(text(importadas))}</strong>
          </div>

          <div class="detail-field">
            <span>Incidencias registradas</span>
            <strong>${escapeHtml(text(incidencias))}</strong>
          </div>

          <div class="detail-field">
            <span>Casos con error monetario</span>
            <strong>${escapeHtml(text(erroresMonetarios))}</strong>
          </div>
        </div>

        ${
          Number(erroresMonetarios || 0) > 0
            ? `
              <div class="notice note-warning monetary-warning">
                <strong>Hay importes que requieren revisión.</strong>
                <p>
                  Los casos se conservaron, pero los importes originales
                  no pudieron convertirse correctamente. Revisa el detalle
                  de cada caso antes de tomar decisiones.
                </p>
              </div>
            `
            : ''
        }

        ${
          errores.length
            ? `
              <section class="detail-section">
                <h4>Incidencias devueltas por el servidor (${errores.length})</h4>
                <ul class="import-error-list">
                  ${
                    errores.slice(0, 30).map((error) => `
                      <li>${escapeHtml(text(error))}</li>
                    `).join('')
                  }
                </ul>
                ${
                  errores.length > 30
                    ? `<p class="muted">Se muestran los primeros 30 mensajes de ${errores.length}.</p>`
                    : ''
                }
              </section>
            `
            : `
              <p class="muted">El servidor no devolvió mensajes de error.</p>
            `
        }
      </section>
    `;

    box.classList.remove('hidden');

    state.latestBatch = result;
  }

  function validationHasErrors(result) {
    const errores = getValue(
      result,
      ['errores', 'errors'],
      []
    );

    if (Array.isArray(errores) && errores.length > 0) {
      return true;
    }

    const cantidad = countNumber(
      getValue(
        result,
        ['cantidadErrores', 'totalErrores', 'errorCount'],
        0
      )
    );

    return cantidad !== null && cantidad > 0;
  }

  async function validateFile(event) {
    event.preventDefault();

    const input = $('excelFile');
    const file = input.files && input.files[0];

    if (!file) {
      showToast('Selecciona un archivo .xlsx.', 'error');
      return;
    }

    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      showToast('Solo se aceptan archivos con extensión .xlsx.', 'error');
      return;
    }

    state.pendingFile = file;

    const button = $('validateFileButton');
    setButtonBusy(button, true, 'Validando…');
    $('importFileButton').disabled = true;

    try {
      const form = new FormData();
      form.append('archivo', file);

      const result = await api(
        '/api/casos/carga/validar',
        { method: 'POST', body: form }
      );

      showUploadResult(result, 'success');

      const explicitlyInvalid =
        getValue(result, ['valido', 'valid', 'apto']) === false ||
        validationHasErrors(result);

      $('importFileButton').disabled = explicitlyInvalid;

      showToast(
        explicitlyInvalid
          ? 'La validación encontró errores; corrige el archivo antes de importarlo.'
          : 'Validación completada. Revisa el resultado antes de importar.',
        explicitlyInvalid ? 'error' : 'success'
      );
    } catch (error) {
      state.pendingFile = null;

      showUploadResult(error.message, 'error');
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function importFile() {
    const file = state.pendingFile ||
      ($('excelFile').files && $('excelFile').files[0]);

    if (!file) {
      showToast(
        'Selecciona y valida un archivo antes de importarlo.',
        'error'
      );
      return;
    }

    if (!window.confirm(
      `Vas a importar “${file.name}”. Esta operación puede cambiar los datos del entorno. ¿Continuar?`
    )) {
      return;
    }

    const button = $('importFileButton');
    setButtonBusy(button, true, 'Importando…');

    try {
      const form = new FormData();
      form.append('archivo', file);

      const result = await api(
        '/api/casos/carga',
        { method: 'POST', body: form }
      );

      showImportResult(result);

      const nested = getValue(result, ['data', 'resultado'], undefined);
      const summary = nested && typeof nested === 'object'
        ? nested
        : result;

      const exitoso = getValue(
        summary,
        ['exitoso', 'success'],
        true
      ) !== false;

      const incidencias = countNumber(
        getValue(summary, ['filasConError', 'incidencias'], 0)
      ) ?? 0;

      const monetarios = countNumber(
        getValue(
          summary,
          ['casosConErrorMonetario', 'erroresMonetarios'],
          0
        )
      ) ?? 0;

      if (!exitoso) {
        showToast(
          'La importación no se completó. Revisa el resultado mostrado.',
          'error'
        );
      } else if (incidencias > 0 || monetarios > 0) {
        showToast(
          `Importación terminada con incidencias: ${incidencias} registradas, ${monetarios} casos con errores monetarios.`,
          'error'
        );
      } else {
        showToast(
          'Importación completada sin incidencias reportadas.',
          'success'
        );
      }

      state.pendingFile = null;
      $('importFileButton').disabled = true;

      await loadDashboard();
    } catch (error) {
      showUploadResult(error.message, 'error');
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  function isBatchObject(data) {
    if (!data || typeof data !== 'object') return null;

    return getValue(
      data,
      ['lote', 'paymentBatch', 'batch', 'resultado'],
      data
    );
  }

  function displayBatch(data, mode) {
    const result = isBatchObject(data) || {};

    state.latestBatch = result;

    const batchId = getValue(
      result,
      ['idLote', 'loteId', 'id', 'identificadorLote', 'batchId'],
      ''
    );

    const batchCode = getValue(
      result,
      ['codigoLote', 'numeroLote', 'identificador', 'codigo'],
      ''
    );

    const quantity = getValue(
      result,
      ['cantidadCasos', 'cantidad', 'totalCasos', 'numeroCasos'],
      '—'
    );

    const totalValue = getValue(
      result,
      ['valorTotal', 'totalValor', 'montoTotal', 'valor'],
      ''
    );

    const status = getValue(
      result,
      ['estado', 'estadoLote', 'status'],
      mode === 'paid' ? 'PAGADO' : 'GENERADO'
    );

    const date = getValue(
      result,
      ['fechaCreacion', 'fecha', 'fechaGeneracion', 'createdAt'],
      ''
    );

    $('batchResult').className = 'batch-result';

    $('batchResult').innerHTML = `
      <div class="detail-grid">
        <div class="detail-field">
          <span>Identificador</span>
          <strong>${escapeHtml(text(batchCode || batchId))}</strong>
        </div>

        <div class="detail-field">
          <span>Estado</span>
          <strong>${escapeHtml(prettyLabel(status))}</strong>
        </div>

        <div class="detail-field">
          <span>Casos incluidos</span>
          <strong>${escapeHtml(text(quantity))}</strong>
        </div>

        <div class="detail-field">
          <span>Valor total</span>
          <strong>${escapeHtml(totalValue === '' ? 'No informado' : currency(totalValue))}</strong>
        </div>

        <div class="detail-field">
          <span>Fecha de generación</span>
          <strong>${escapeHtml(text(date))}</strong>
        </div>

        <div class="detail-field">
          <span>ID numérico para confirmar</span>
          <strong>${escapeHtml(text(batchId, 'No informado'))}</strong>
        </div>
      </div>

      <details class="detail-section">
        <summary>Respuesta completa del servidor</summary>
        <pre class="result-json">${escapeHtml(JSON.stringify(data, null, 2))}</pre>
      </details>
    `;

    if (batchId !== '') {
      $('batchIdInput').value = batchId;
      $('payBatchControls').classList.remove('hidden');
    } else {
      $('payBatchControls').classList.toggle(
        'hidden',
        state.role !== 'ADMIN'
      );

      if (state.role === 'ADMIN') {
        $('payBatchControls').classList.remove('hidden');
      }
    }
  }

  async function generateBatch() {
    if (!window.confirm(
      '¿Generar un lote con todos los casos aprobados elegibles ahora?'
    )) {
      return;
    }

    const button = $('generateBatchButton');
    setButtonBusy(button, true, 'Generando…');

    try {
      const result = await api(
        '/api/lotes',
        { method: 'POST' }
      );

      displayBatch(result, 'created');

      showToast(
        'Lote generado. Verifica cantidad, valor e identificador.',
        'success'
      );

      await loadDashboard();
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function payBatch() {
    const id = $('batchIdInput').value.trim();

    if (!/^\d+$/.test(id)) {
      showToast('Ingresa el ID numérico del lote.', 'error');
      return;
    }

    if (state.role !== 'ADMIN') {
      showToast('Solo ADMIN puede confirmar pagos.', 'error');
      return;
    }

    if (!window.confirm(
      `Confirmar el pago simulado del lote ${id}. ¿Continuar?`
    )) {
      return;
    }

    const button = $('payBatchButton');
    setButtonBusy(button, true, 'Confirmando…');

    try {
      const result = await api(
        `/api/lotes/${encodeURIComponent(id)}/pagar`,
        { method: 'PATCH' }
      );

      displayBatch(result, 'paid');

      showToast(
        'El backend confirmó la operación. Verifica el estado devuelto.',
        'success'
      );

      await loadDashboard();
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function loadParameters() {
    $('parametersList').innerHTML =
      '<p class="table-empty">Cargando parámetros…</p>';

    if (state.role !== 'ADMIN') return;

    try {
      const result = await api('/api/parametros');
      const params = firstArray(result);

      $('parametersCaption').textContent =
        `${params.length} parámetro(s) disponibles`;

      if (!params.length) {
        $('parametersList').innerHTML = `
          <pre class="result-json">${escapeHtml(JSON.stringify(result, null, 2))}</pre>
        `;
        return;
      }

      $('parametersList').innerHTML = params.map((item) => {
        const key = text(
          getValue(item, ['clave', 'key', 'nombre', 'codigo'], '')
        );

        const value = text(
          getValue(item, ['valor', 'parameterValue', 'valorParametro', 'value'], '')
        );

        const description = text(
          getValue(item, ['descripcion', 'descripcionParametro', 'ayuda', 'description'], '')
        );

        return `
          <form
            class="parameter-row"
            data-parameter-form="${escapeHtml(key)}">

            <div class="parameter-info">
              <strong>${escapeHtml(key)}</strong>
              <small>${escapeHtml(description)}</small>
            </div>

            <div>
              <label for="param-${escapeHtml(key)}">Valor actual / nuevo</label>
              <input
                id="param-${escapeHtml(key)}"
                name="valor"
                maxlength="4000"
                required
                value="${escapeHtml(value)}">
            </div>

            <div class="parameter-reason">
              <label for="reason-${escapeHtml(key)}">Motivo del cambio</label>
              <input
                id="reason-${escapeHtml(key)}"
                name="motivo"
                minlength="${MIN_REASON}"
                maxlength="500"
                required
                placeholder="Justifica el cambio (mín. 10 caracteres)">
            </div>

            <button class="button button-outline" type="submit">
              Guardar
            </button>
          </form>
        `;
      }).join('');
    } catch (error) {
      $('parametersCaption').textContent =
        'No fue posible consultar los parámetros';

      $('parametersList').innerHTML = `
        <p class="table-empty">${escapeHtml(error.message)}</p>
      `;
    }
  }

  async function saveParameter(form) {
    const key = form.dataset.parameterForm;
    const data = new FormData(form);

    const valor = String(data.get('valor') ?? '').trim();
    const motivo = String(data.get('motivo') ?? '').trim();

    if (motivo.length < MIN_REASON) {
      showToast('El motivo debe tener al menos 10 caracteres.', 'error');
      return;
    }

    const button = form.querySelector('button[type="submit"]');
    setButtonBusy(button, true, 'Guardando…');

    try {
      const result = await api(
        `/api/parametros/${encodeURIComponent(key)}`,
        {
          method: 'PUT',
          body: JSON.stringify({ valor, motivo })
        }
      );

      showToast(`Parámetro ${key} actualizado.`, 'success');

      await loadParameters();

      if (result) {
        $('parametersCaption').textContent +=
          ' · Último cambio guardado';
      }
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      if (button.isConnected) {
        setButtonBusy(button, false);
      }
    }
  }

  async function exportCsv() {
    const button = $('exportButton');
    setButtonBusy(button, true, 'Preparando…');

    try {
      const response = await api('/api/reportes/exportar');

      if (!(response instanceof Response)) {
        throw new Error(
          'La exportación no devolvió un archivo descargable.'
        );
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');

      anchor.href = url;
      anchor.download = 'resultados_reclamaciones_prueba.csv';

      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();

      URL.revokeObjectURL(url);

      showToast(
        'La descarga del archivo de resultados se inició.',
        'success'
      );
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setButtonBusy(button, false);
    }
  }

  async function login(event) {
    event.preventDefault();

    const button = $('loginButton');
    $('loginError').textContent = '';

    const username = $('username').value.trim();
    const password = $('password').value;

    setButtonBusy(button, true, 'Validando…');

    try {
      const response = await api(
        '/api/auth/login',
        {
          method: 'POST',
          body: JSON.stringify({ username, password })
        }
      );

      const token = getValue(
        response,
        ['token', 'accessToken', 'jwt', 'access_token']
      );

      if (!token || typeof token !== 'string') {
        throw new Error(
          'El login respondió sin un token JWT reconocible. Revisa el contrato de /api/auth/login.'
        );
      }

      state.token = token;

      state.username = text(
        getValue(
          response,
          ['username', 'usuario', 'nombreUsuario'],
          username
        )
      );

      state.role = deriveRole(response, token, username);

      sessionStorage.setItem('sura_jwt', state.token);
      sessionStorage.setItem('sura_username', state.username);
      sessionStorage.setItem('sura_role', state.role);

      enterApplication();

      showToast(`Sesión iniciada como ${state.role}.`, 'success');
    } catch (error) {
      $('loginError').textContent = error.message;
    } finally {
      setButtonBusy(button, false);
    }
  }

  function bindEvents() {
    $('loginForm').addEventListener('submit', login);

    $('logoutButton').addEventListener('click', () => {
      clearSession();
      showToast('Sesión cerrada.');
    });

    document.querySelectorAll('[data-view]').forEach((button) => {
      button.addEventListener('click', () => {
        showView(button.dataset.view);
      });
    });

    document.querySelectorAll('[data-open-view]').forEach((button) => {
      button.addEventListener('click', () => {
        showView(button.dataset.openView);
      });
    });

    $('mobileMenuButton').addEventListener('click', () => {
      $('sidebar').classList.toggle('open');
    });

    $('refreshDashboard').addEventListener('click', loadDashboard);

    const approvalCards = $('approvalCards');

    if (approvalCards) {
      approvalCards.addEventListener('click', (event) => {
        const button = event.target.closest('[data-open-approval]');

        if (!button) return;

        const estadoObjetivo = button.dataset.openApproval;

        const estadosPermitidos = [
          'APROBADO_AUTOMATICO',
          'APROBADO_ANALISTA'
        ];

        if (!estadosPermitidos.includes(estadoObjetivo)) {
          return;
        }

        $('caseSearch').value = '';
        $('filterEstado').value = estadoObjetivo;
        $('filterDecision').value = '';
        $('filterPrioridad').value = '';
        $('filterRegla').value = '';
        $('filterSemaforo').value = '';

        state.page = 0;

        showView('cases');
      });
    }

    $('applyFilters').addEventListener('click', () => {
      state.page = 0;
      loadCases();
    });

    $('caseSearch').addEventListener('input', () => {
      state.page = 0;

      clearTimeout(window.__caseSearchTimer);
      window.__caseSearchTimer = setTimeout(loadCases, 250);
    });

    $('pageSize').addEventListener('change', () => {
      state.page = 0;
      loadCases();
    });

    $('previousPage').addEventListener('click', () => {
      state.page = Math.max(0, state.page - 1);
      loadCases();
    });

    $('nextPage').addEventListener('click', () => {
      if (state.page + 1 < state.totalPages) {
        state.page += 1;
        loadCases();
      }
    });

    $('casesTableBody').addEventListener('click', (event) => {
      const button = event.target.closest('[data-case-detail]');

      if (button) {
        openCaseDetail(button.dataset.caseDetail);
      }
    });

    $('caseModalBody').addEventListener('click', (event) => {
      const take = event.target.closest('[data-take-case]');
      const decision = event.target.closest('[data-decision]');

      if (take) {
        takeCase(take.dataset.takeCase);
      }

      if (decision) {
        openDecisionModal(
          decision.dataset.code,
          decision.dataset.decision
        );
      }
    });

    $('closeCaseModal').addEventListener('click', () => {
      $('caseModal').classList.add('hidden');
    });

    $('closeDecisionModal').addEventListener('click', () => {
      $('decisionModal').classList.add('hidden');
    });

    $('cancelDecisionButton').addEventListener('click', () => {
      $('decisionModal').classList.add('hidden');
    });

    $('decisionForm').addEventListener('submit', submitDecision);

    $('decisionObservation').addEventListener('input', () => {
      $('observationCount').textContent =
        String($('decisionObservation').value.length);
    });

    $('evaluateButton').addEventListener('click', runEvaluation);

    $('uploadForm').addEventListener('submit', validateFile);
    $('importFileButton').addEventListener('click', importFile);

    $('excelFile').addEventListener('change', (event) => {
      const file = event.target.files && event.target.files[0];

      $('fileName').textContent = file
        ? file.name
        : 'Arrastra tu archivo aquí o selecciónalo';

      $('importFileButton').disabled = true;
      state.pendingFile = null;

      $('uploadResult').classList.add('hidden');
    });

    const drop = $('dropZone');

    ['dragenter', 'dragover'].forEach((name) => {
      drop.addEventListener(name, (event) => {
        event.preventDefault();
        drop.classList.add('dragover');
      });
    });

    ['dragleave', 'drop'].forEach((name) => {
      drop.addEventListener(name, (event) => {
        event.preventDefault();
        drop.classList.remove('dragover');
      });
    });

    drop.addEventListener('drop', (event) => {
      const file = event.dataTransfer.files &&
        event.dataTransfer.files[0];

      if (!file) return;

      const transfer = new DataTransfer();
      transfer.items.add(file);

      $('excelFile').files = transfer.files;

      $('excelFile').dispatchEvent(
        new Event('change', { bubbles: true })
      );
    });

    $('generateBatchButton').addEventListener('click', generateBatch);
    $('payBatchButton').addEventListener('click', payBatch);
    $('reloadParametersButton').addEventListener('click', loadParameters);

    $('parametersList').addEventListener('submit', (event) => {
      const form = event.target.closest('[data-parameter-form]');

      if (form) {
        event.preventDefault();
        saveParameter(form);
      }
    });

    $('exportButton').addEventListener('click', exportCsv);

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        $('caseModal').classList.add('hidden');
        $('decisionModal').classList.add('hidden');
        $('sidebar').classList.remove('open');
      }
    });
  }

  bindEvents();

  if (state.token) {
    enterApplication();

    api('/api/reportes/resumen').catch(() => {
      // api() limpiará el token si recibe HTTP 401.
    });
  }

// --- Integración de RugIA para la regla R17 ---
function renderRugiaAnalysis(detail) {
    const analysis = detail && (detail.analisisR17 || detail.analisis_r17);
    let resultado = "Sin analisis disponible";
    let mensaje = "Esta evaluacion aun no tiene un analisis R17 guardado.";
    let clase = "notice";

    if (analysis && typeof analysis === "object") {
        if (analysis.cumpleR17 === false || String(analysis.comparacion || "").toUpperCase() === "DISCREPANCIA") {
            resultado = "Requiere revision manual";
            mensaje = "El resultado de R17 necesita revision humana antes de tomar una decision.";
            clase = "notice note-warning";
        } else if (analysis.cumpleR17 === true) {
            resultado = "R17 cumple";
            mensaje = "No se detectaron indicios que incumplan R17.";
        } else {
            resultado = "Requiere revision manual";
            mensaje = "No se pudo confirmar el resultado de R17 con seguridad.";
            clase = "notice note-warning";
        }
    }

    const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    })[char]);

    return `
        <section class="detail-section">
            <h3>An\u00e1lisis RugIA</h3>
            <div class="${clase}">
                <strong>${escapeHtml(resultado)}</strong>
                <p>${escapeHtml(mensaje)}</p>
            </div>
        </section>`;
}
function ensureRugiaModeSelector() {
    const textInputs = Array.from(document.querySelectorAll('input[type="text"], input:not([type])'));

    for (const input of textInputs) {
        let ancestor = input.parentElement;
        while (ancestor && ancestor !== document.body) {
            if ((ancestor.textContent || "").includes("R17_MODO_ANALISIS")) {
                const fields = Array.from(ancestor.querySelectorAll('input[type="text"], input:not([type])'));
                if (fields.length === 1 && fields[0] === input) {
                    const select = document.createElement("select");
                    for (const attribute of Array.from(input.attributes)) {
                        if (["id", "name", "class", "title", "required", "disabled", "style", "aria-label", "aria-describedby", "data-testid"].includes(attribute.name) || attribute.name.startsWith("data-")) {
                            select.setAttribute(attribute.name, attribute.value);
                        }
                    }
                    select.setAttribute("data-rugia-mode", "true");
                    const options = [
                        ["TEXTOS", "TEXTOS — detector de palabras actual"],
                        ["IA", "IA — clasificación con RugIA"],
                        ["AMBAS", "AMBAS — comparar texto e IA"]
                    ];
                    for (const [value, label] of options) {
                        const option = document.createElement("option");
                        option.value = value;
                        option.textContent = label;
                        select.appendChild(option);
                    }
                    const current = String(input.value || "TEXTOS").trim().toUpperCase();
                    select.value = options.some(([value]) => value === current) ? current : "TEXTOS";
                    input.replaceWith(select);
                    break;
                }
                // Si el contenedor corresponde a varios campos, subir al siguiente nivel no es seguro.
                break;
            }
            ancestor = ancestor.parentElement;
        }
    }
}

function startRugiaModeSelectorObserver() {
    if (!document.body || typeof MutationObserver === "undefined") return;
    ensureRugiaModeSelector();
    const observer = new MutationObserver(() => ensureRugiaModeSelector());
    observer.observe(document.body, { childList: true, subtree: true });
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", startRugiaModeSelectorObserver, { once: true });
} else {
    startRugiaModeSelectorObserver();
}
// --- Fin integración de RugIA ---
})();