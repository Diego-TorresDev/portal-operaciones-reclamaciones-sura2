-- ============================================================================
-- SURA | Portal de Operaciones de Reclamaciones de Salud
-- Migracion V1: esquema inicial de la base de datos PostgreSQL.
--
-- El script crea las tablas necesarias para importar el Excel, conservar las
-- tablas de referencia, evaluar las 17 reglas, calcular ANS, auditar cambios,
-- gestionar la bandeja manual y simular lotes de pago.
--
-- Este archivo se ejecuta mediante Flyway. No se debe volver a ejecutar a mano
-- despues de que Flyway registre la version V1 como aplicada.
-- ============================================================================

-- --------------------------------------------------------------------------
-- 1. Usuarios y seguridad
-- Las contrasenas se guardan unicamente como hashes BCrypt, nunca en texto
-- claro. Los usuarios de prueba se crearan desde la aplicacion con un servicio
-- de inicializacion que lea las contrasenas desde variables de entorno.
-- --------------------------------------------------------------------------
CREATE TABLE app_user (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username            VARCHAR(80) NOT NULL UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    display_name        VARCHAR(150) NOT NULL,
    role                VARCHAR(20) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_role CHECK (role IN ('ANALISTA', 'ADMIN')),
    CONSTRAINT ck_app_user_username_lower CHECK (username = LOWER(username))
);

COMMENT ON TABLE app_user IS 'Usuarios que pueden acceder al portal; password_hash contiene el hash BCrypt.';
COMMENT ON COLUMN app_user.role IS 'Rol funcional: ANALISTA o ADMIN. La autorizacion se aplica tambien en la API.';

-- --------------------------------------------------------------------------
-- 2. Cargas del Excel y errores de importacion
-- Se guarda el resumen de cada intento de carga. Los errores por fila no deben
-- incluir contrasenas ni duplicar datos personales innecesariamente.
-- --------------------------------------------------------------------------
CREATE TABLE import_batch (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_filename   VARCHAR(255) NOT NULL,
    file_sha256         CHAR(64),
    status              VARCHAR(30) NOT NULL DEFAULT 'RECIBIDO',
    uploaded_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMPTZ,
    total_rows          INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    rows_read           INTEGER NOT NULL DEFAULT 0 CHECK (rows_read >= 0),
    rows_valid          INTEGER NOT NULL DEFAULT 0 CHECK (rows_valid >= 0),
    rows_with_errors    INTEGER NOT NULL DEFAULT 0 CHECK (rows_with_errors >= 0),
    summary             JSONB NOT NULL DEFAULT '{}'::JSONB,
    failure_message     TEXT,
    CONSTRAINT ck_import_batch_status CHECK (
        status IN ('RECIBIDO', 'PROCESANDO', 'COMPLETADO', 'COMPLETADO_CON_ERRORES', 'FALLIDO')
    ),
    CONSTRAINT ck_import_batch_hash CHECK (
        file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-fA-F]{64}$'
    )
);

CREATE INDEX idx_import_batch_started_at ON import_batch(started_at DESC);
CREATE INDEX idx_import_batch_status ON import_batch(status);

CREATE TABLE import_row_error (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_batch_id BIGINT NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE,
    sheet_name      VARCHAR(100) NOT NULL,
    row_number      INTEGER NOT NULL CHECK (row_number > 0),
    column_name     VARCHAR(150),
    error_code      VARCHAR(80) NOT NULL,
    severity        VARCHAR(15) NOT NULL DEFAULT 'ERROR',
    error_message   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_import_row_error_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR'))
);

CREATE INDEX idx_import_row_error_batch ON import_row_error(import_batch_id, sheet_name, row_number);

COMMENT ON TABLE import_row_error IS 'Errores de validacion de la carga, separados de la bitacora de seguridad.';

-- --------------------------------------------------------------------------
-- 3. Tablas de referencia correspondientes a las hojas 03 a 07 del Excel.
-- Se mantienen los valores crudos adicionales en extra_data para no perder
-- columnas de referencia que deban confirmarse al revisar el Excel original.
-- --------------------------------------------------------------------------
CREATE TABLE insurance_plan (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_code                       VARCHAR(80) NOT NULL UNIQUE,
    plan_name                       VARCHAR(180) NOT NULL,
    max_auto_approval_amount        NUMERIC(16,2) NOT NULL CHECK (max_auto_approval_amount >= 0),
    annual_cap                      NUMERIC(16,2) NOT NULL CHECK (annual_cap >= 0),
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    extra_data                      JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE policy (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_number       VARCHAR(100) NOT NULL UNIQUE,
    status              VARCHAR(40) NOT NULL,
    start_date          DATE,
    end_date            DATE,
    plan_id             BIGINT REFERENCES insurance_plan(id) ON DELETE RESTRICT,
    annual_consumed_amount NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (annual_consumed_amount >= 0),
    affiliate_name      VARCHAR(200),
    affiliate_document  VARCHAR(80),
    extra_data          JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);

CREATE INDEX idx_policy_status ON policy(status);
CREATE INDEX idx_policy_plan ON policy(plan_id);

CREATE TABLE provider (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nit         VARCHAR(40) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    in_network  BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_alert BOOLEAN NOT NULL DEFAULT FALSE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    extra_data  JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_provider_network_fraud ON provider(in_network, fraud_alert);

CREATE TABLE diagnosis (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    diagnosis_code              VARCHAR(60) NOT NULL UNIQUE,
    description                 VARCHAR(250) NOT NULL,
    excluded                    BOOLEAN NOT NULL DEFAULT FALSE,
    requires_medical_review     BOOLEAN NOT NULL DEFAULT FALSE,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    extra_data                  JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diagnosis_excluded_review ON diagnosis(excluded, requires_medical_review);

CREATE TABLE service_catalog (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_code                VARCHAR(100) NOT NULL UNIQUE,
    service_name                VARCHAR(200) NOT NULL,
    supports_required           BOOLEAN NOT NULL DEFAULT FALSE,
    required_supports_description TEXT,
    required_supports            JSONB NOT NULL DEFAULT '[]'::JSONB,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    extra_data                  JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_service_required_supports_json CHECK (jsonb_typeof(required_supports) = 'array')
);

-- --------------------------------------------------------------------------
-- 4. Catalogo de estados y transiciones permitidas.
-- La aplicacion consultara estas reglas antes de cambiar el estado de un caso.
-- Las transiciones de reevaluacion solo aplican a casos aun no tomados por un
-- analista ni incluidos en un lote.
-- --------------------------------------------------------------------------
CREATE TABLE case_state_definition (
    state_code      VARCHAR(40) PRIMARY KEY,
    display_name    VARCHAR(100) NOT NULL,
    description     TEXT NOT NULL,
    terminal        BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   SMALLINT NOT NULL
);

INSERT INTO case_state_definition(state_code, display_name, description, terminal, display_order) VALUES
('RADICADO', 'Radicado', 'El caso se leyó del archivo de entrada.', FALSE, 1),
('VALIDADO', 'Validado', 'Los datos se normalizaron y se ejecutaron las validaciones de estructura.', FALSE, 2),
('EVALUADO', 'Evaluado', 'Se ejecutaron las 17 reglas y se guardó el resultado.', FALSE, 3),
('APROBADO_AUTOMATICO', 'Aprobado automáticamente', 'El caso cumple todas las reglas y queda listo para pago.', FALSE, 4),
('EN_BANDEJA', 'En bandeja manual', 'El caso requiere revisión de una persona.', FALSE, 5),
('EN_REVISION', 'En revisión', 'Un analista tomó el caso y quedó asignado.', FALSE, 6),
('APROBADO_ANALISTA', 'Aprobado por analista', 'El analista asignado aprobó el caso con observación.', FALSE, 7),
('OBJETADO', 'Objetado', 'El analista asignado objetó el caso con observación.', TRUE, 8),
('EN_LOTE_PAGO', 'En lote de pago', 'El caso está incluido en un lote de pago.', FALSE, 9),
('PAGADO', 'Pagado', 'El pago del lote fue confirmado de forma simulada.', FALSE, 10),
('CERRADO', 'Cerrado', 'El flujo del caso terminó.', TRUE, 11);

CREATE TABLE case_state_transition (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_state          VARCHAR(40) NOT NULL REFERENCES case_state_definition(state_code),
    to_state            VARCHAR(40) NOT NULL REFERENCES case_state_definition(state_code),
    condition_code      VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
    condition_description TEXT NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_case_state_transition UNIQUE (from_state, to_state, condition_code),
    CONSTRAINT ck_case_state_transition_not_same CHECK (from_state <> to_state)
);

INSERT INTO case_state_transition(from_state, to_state, condition_code, condition_description) VALUES
('RADICADO', 'VALIDADO', 'NORMAL', 'La fila fue leida y sus datos se normalizaron.'),
('VALIDADO', 'EVALUADO', 'NORMAL', 'La evaluacion de las 17 reglas terminó.'),
('EVALUADO', 'APROBADO_AUTOMATICO', 'NORMAL', 'Todas las reglas se cumplieron.'),
('EVALUADO', 'EN_BANDEJA', 'NORMAL', 'Al menos una regla falló.'),
('EN_BANDEJA', 'EN_REVISION', 'NORMAL', 'Un analista toma el caso y queda asignado.'),
('EN_REVISION', 'APROBADO_ANALISTA', 'NORMAL', 'El analista asignado aprueba con observacion válida.'),
('EN_REVISION', 'OBJETADO', 'NORMAL', 'El analista asignado objeta con observacion válida.'),
('APROBADO_AUTOMATICO', 'EN_LOTE_PAGO', 'NORMAL', 'El caso se incorpora a un lote de pago.'),
('APROBADO_ANALISTA', 'EN_LOTE_PAGO', 'NORMAL', 'El caso se incorpora a un lote de pago.'),
('EN_LOTE_PAGO', 'PAGADO', 'NORMAL', 'Un administrador confirma el pago simulado.'),
('PAGADO', 'CERRADO', 'NORMAL', 'El lote pagado cierra el ciclo del caso.'),
('OBJETADO', 'CERRADO', 'NORMAL', 'Un caso objetado se cierra automáticamente.'),
('APROBADO_AUTOMATICO', 'EN_BANDEJA', 'REEVALUACION', 'Una reevaluacion detecta reglas incumplidas antes de entrar a un lote.'),
('EN_BANDEJA', 'APROBADO_AUTOMATICO', 'REEVALUACION', 'Una reevaluacion confirma que ahora cumple todas las reglas.');

-- --------------------------------------------------------------------------
-- 5. Definicion de las 17 reglas.
-- Los resultados de cada ejecucion se guardaran en case_evaluation_rule, no en
-- esta tabla de catalogo. Asi se conserva la explicacion de cada evaluacion.
-- --------------------------------------------------------------------------
CREATE TABLE rule_definition (
    rule_code       VARCHAR(10) PRIMARY KEY,
    rule_name       VARCHAR(160) NOT NULL,
    description     TEXT NOT NULL,
    base_priority   VARCHAR(10) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   SMALLINT NOT NULL UNIQUE,
    CONSTRAINT ck_rule_definition_priority CHECK (base_priority IN ('ALTA', 'MEDIA', 'BAJA'))
);

INSERT INTO rule_definition(rule_code, rule_name, description, base_priority, display_order) VALUES
('R01', 'Calidad de datos', 'Documento, póliza, valor, fecha de servicio y fecha de radicación deben estar diligenciados; el valor debe ser mayor que cero y las fechas deben existir.', 'BAJA', 1),
('R02', 'Coherencia de fechas', 'La fecha del servicio no puede ser posterior a la fecha de radicación.', 'BAJA', 2),
('R03', 'Posible duplicado', 'Un caso previo tiene la misma póliza, NIT del prestador, fecha de servicio y valor. El primer caso no se marca.', 'MEDIA', 3),
('R04', 'Póliza existente', 'La póliza debe existir en la tabla de referencia 03_Polizas, comparando sin espacios y en mayúsculas.', 'MEDIA', 4),
('R05', 'Estado de la póliza', 'La póliza debe tener estado Vigente.', 'MEDIA', 5),
('R06', 'Vigencia de la póliza', 'La fecha del servicio debe estar dentro de la vigencia de la póliza, incluyendo ambas fechas límite.', 'MEDIA', 6),
('R07', 'Oportunidad de radicación', 'Los días calendario entre servicio y radicación no deben superar PLAZO_RADICACION_DIAS.', 'MEDIA', 7),
('R08', 'Red de prestadores', 'El NIT debe existir en 04_Prestadores y el prestador debe estar en red.', 'MEDIA', 8),
('R09', 'Alerta de fraude', 'El prestador no debe tener una alerta de fraude activa.', 'ALTA', 9),
('R10', 'Tipo de servicio', 'El tipo de servicio debe existir en 06_Servicios.', 'MEDIA', 10),
('R11', 'Soportes requeridos', 'El caso debe contener los soportes exigidos para el servicio; Si, si, Sí y S cuentan como afirmativo.', 'BAJA', 11),
('R12', 'Diagnóstico', 'El código debe existir en 05_Diagnosticos y no estar marcado como excluido.', 'MEDIA', 12),
('R13', 'Revisión médica', 'El diagnóstico no debe requerir revisión médica.', 'ALTA', 13),
('R14', 'Hospitalización', 'El tipo de servicio no puede ser Hospitalización.', 'MEDIA', 14),
('R15', 'Monto máximo automático', 'El valor reclamado no debe superar el monto máximo de aprobación automática correspondiente al plan; la igualdad cumple.', 'MEDIA', 15),
('R16', 'Tope anual', 'El valor consumido del año más el valor reclamado no debe superar el tope anual del plan.', 'ALTA', 16),
('R17', 'Observaciones', 'Las observaciones no deben indicar accidente de tránsito, accidente laboral, responsabilidad de terceros ni procedimiento estético; debe considerarse el sentido de las negaciones.', 'MEDIA', 17);

-- --------------------------------------------------------------------------
-- 6. Casos reclamados.
-- Los códigos y valores originales se guardan aunque estén mal formados o no
-- existan en las tablas de referencia. Los IDs resueltos son opcionales para
-- que una póliza o diagnóstico inválido no impida importar y enviar a revisión.
-- raw_payload conserva la fila original del Excel para auditoría funcional.
-- --------------------------------------------------------------------------
CREATE TABLE claim_case (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_code                   VARCHAR(50) NOT NULL UNIQUE,
    import_batch_id             BIGINT REFERENCES import_batch(id) ON DELETE SET NULL,
    source_sheet_name           VARCHAR(100) NOT NULL DEFAULT '02_Casos',
    source_row_number           INTEGER CHECK (source_row_number IS NULL OR source_row_number > 0),

    affiliate_name              VARCHAR(200),
    document_number             VARCHAR(100),

    policy_number_raw           TEXT,
    normalized_policy_number    VARCHAR(100),
    resolved_policy_id          BIGINT REFERENCES policy(id) ON DELETE SET NULL,

    provider_nit_raw            TEXT,
    normalized_provider_nit     VARCHAR(40),
    resolved_provider_id         BIGINT REFERENCES provider(id) ON DELETE SET NULL,

    service_type_raw            TEXT,
    normalized_service_type     VARCHAR(100),
    resolved_service_type_id     BIGINT REFERENCES service_catalog(id) ON DELETE SET NULL,

    diagnosis_code_raw          TEXT,
    normalized_diagnosis_code   VARCHAR(60),
    resolved_diagnosis_id        BIGINT REFERENCES diagnosis(id) ON DELETE SET NULL,

    claim_amount_raw            TEXT,
    claim_amount                NUMERIC(16,2) CHECK (claim_amount IS NULL OR claim_amount >= 0),
    service_date_raw            TEXT,
    service_date                DATE,
    filing_date_raw             TEXT,
    filing_date                 DATE,

    -- Los soportes pueden venir en varias columnas del Excel; JSONB conserva
    -- el detalle hasta que el importador conozca los encabezados exactos.
    supports_payload            JSONB NOT NULL DEFAULT '{}'::JSONB,
    observations                TEXT,
    raw_payload                 JSONB NOT NULL DEFAULT '{}'::JSONB,

    current_state               VARCHAR(40) NOT NULL DEFAULT 'RADICADO'
                                REFERENCES case_state_definition(state_code),
    current_decision            VARCHAR(20),
    current_priority            VARCHAR(10),
    assigned_analyst_id         BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    assigned_at                 TIMESTAMPTZ,

    ans_deadline_date           DATE,
    ans_semaphore               VARCHAR(20),
    ans_alert                   TEXT,
    closed_at                   TIMESTAMPTZ,

    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_claim_case_decision CHECK (
        current_decision IS NULL OR current_decision IN ('AUTOMATICO', 'MANUAL')
    ),
    CONSTRAINT ck_claim_case_priority CHECK (
        current_priority IS NULL OR current_priority IN ('ALTA', 'MEDIA', 'BAJA')
    ),
    CONSTRAINT ck_claim_case_semaphore CHECK (
        ans_semaphore IS NULL OR ans_semaphore IN ('VERDE', 'AMARILLO', 'ROJO', 'CUMPLIDO')
    ),
    CONSTRAINT ck_claim_case_json CHECK (
        jsonb_typeof(supports_payload) = 'object' AND jsonb_typeof(raw_payload) = 'object'
    )
);

CREATE INDEX idx_claim_case_state ON claim_case(current_state);
CREATE INDEX idx_claim_case_decision_priority ON claim_case(current_decision, current_priority);
CREATE INDEX idx_claim_case_ans ON claim_case(ans_semaphore, ans_deadline_date);
CREATE INDEX idx_claim_case_assignee ON claim_case(assigned_analyst_id, current_state);
CREATE INDEX idx_claim_case_policy_normalized ON claim_case(normalized_policy_number);
CREATE INDEX idx_claim_case_provider_normalized ON claim_case(normalized_provider_nit);
CREATE INDEX idx_claim_case_diagnosis_normalized ON claim_case(normalized_diagnosis_code);
CREATE INDEX idx_claim_case_service_date_amount ON claim_case(service_date, claim_amount);
CREATE INDEX idx_claim_case_import_batch ON claim_case(import_batch_id);

COMMENT ON TABLE claim_case IS 'Caso de reclamacion. Los valores de entrada se conservan aunque sean invalidos para que puedan ir a revision manual.';
COMMENT ON COLUMN claim_case.document_number IS 'Documento completo solo para el detalle autorizado; listados y bandejas deben devolver una version enmascarada desde la API.';
COMMENT ON COLUMN claim_case.raw_payload IS 'Fila original del Excel. No se expone directamente en listados ni se escribe completa en los logs.';

-- --------------------------------------------------------------------------
-- 7. Evaluaciones y resultado de cada regla.
-- Una reevaluacion crea un nuevo registro; no sobrescribe el resultado pasado.
-- Cada evaluacion debe tener 17 filas en case_evaluation_rule.
-- --------------------------------------------------------------------------
CREATE TABLE case_evaluation (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_case_id       BIGINT NOT NULL REFERENCES claim_case(id) ON DELETE CASCADE,
    evaluation_number   INTEGER NOT NULL CHECK (evaluation_number > 0),
    decision            VARCHAR(20) NOT NULL,
    priority            VARCHAR(10),
    business_reason     TEXT NOT NULL,
    failed_rule_count   SMALLINT NOT NULL CHECK (failed_rule_count BETWEEN 0 AND 17),
    failed_rule_codes   JSONB NOT NULL DEFAULT '[]'::JSONB,
    parameter_snapshot  JSONB NOT NULL DEFAULT '{}'::JSONB,
    evaluated_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    is_current          BOOLEAN NOT NULL DEFAULT TRUE,
    evaluated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_case_evaluation_number UNIQUE (claim_case_id, evaluation_number),
    CONSTRAINT ck_case_evaluation_decision CHECK (decision IN ('AUTOMATICO', 'MANUAL')),
    CONSTRAINT ck_case_evaluation_priority CHECK (priority IS NULL OR priority IN ('ALTA', 'MEDIA', 'BAJA')),
    CONSTRAINT ck_case_evaluation_failed_codes_json CHECK (jsonb_typeof(failed_rule_codes) = 'array'),
    CONSTRAINT ck_case_evaluation_parameters_json CHECK (jsonb_typeof(parameter_snapshot) = 'object')
);

-- Solo una evaluacion puede estar marcada como actual para cada caso.
CREATE UNIQUE INDEX uq_case_evaluation_current
    ON case_evaluation(claim_case_id) WHERE is_current = TRUE;
CREATE INDEX idx_case_evaluation_case_date ON case_evaluation(claim_case_id, evaluated_at DESC);

ALTER TABLE claim_case
    ADD COLUMN current_evaluation_id BIGINT REFERENCES case_evaluation(id) ON DELETE SET NULL;

CREATE TABLE case_evaluation_rule (
    case_evaluation_id BIGINT NOT NULL REFERENCES case_evaluation(id) ON DELETE CASCADE,
    rule_code           VARCHAR(10) NOT NULL REFERENCES rule_definition(rule_code) ON DELETE RESTRICT,
    passed              BOOLEAN NOT NULL,
    business_explanation TEXT NOT NULL,
    evidence            JSONB NOT NULL DEFAULT '{}'::JSONB,
    PRIMARY KEY (case_evaluation_id, rule_code),
    CONSTRAINT ck_case_evaluation_rule_evidence_json CHECK (jsonb_typeof(evidence) = 'object')
);

CREATE INDEX idx_case_evaluation_rule_failures
    ON case_evaluation_rule(rule_code, passed) WHERE passed = FALSE;

COMMENT ON TABLE case_evaluation_rule IS 'Resultado individual de cada regla; guardar tambien las reglas cumplidas permite demostrar que se evaluaron todas las 17.';

-- --------------------------------------------------------------------------
-- 8. Tracking de estados y decisiones de revision manual.
-- Cada evento conserva estado anterior, nuevo estado, actor, fecha y comentario.
-- --------------------------------------------------------------------------
CREATE TABLE case_tracking (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_case_id       BIGINT NOT NULL REFERENCES claim_case(id) ON DELETE CASCADE,
    previous_state      VARCHAR(40) REFERENCES case_state_definition(state_code),
    new_state           VARCHAR(40) NOT NULL REFERENCES case_state_definition(state_code),
    actor_user_id       BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    actor_label         VARCHAR(100) NOT NULL DEFAULT 'SISTEMA',
    comment_text        TEXT,
    event_metadata      JSONB NOT NULL DEFAULT '{}'::JSONB,
    event_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_case_tracking_metadata_json CHECK (jsonb_typeof(event_metadata) = 'object')
);

CREATE INDEX idx_case_tracking_case_time ON case_tracking(claim_case_id, event_at, id);

CREATE TABLE case_review_decision (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_case_id       BIGINT NOT NULL UNIQUE REFERENCES claim_case(id) ON DELETE RESTRICT,
    analyst_user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    action              VARCHAR(20) NOT NULL,
    observation         TEXT NOT NULL,
    decided_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_case_review_action CHECK (action IN ('APROBAR', 'OBJETAR')),
    CONSTRAINT ck_case_review_observation_length CHECK (CHAR_LENGTH(BTRIM(observation)) >= 20)
);

COMMENT ON TABLE case_review_decision IS 'Decision final del analista asignado. El servicio valida ademas el rol y la asignacion antes de insertar.';

-- --------------------------------------------------------------------------
-- 9. Parametros editables y registro de sus cambios.
-- Los cambios se aplican en un servicio transaccional, que registrara tanto la
-- tabla de historial de parametros como la bitacora general.
-- --------------------------------------------------------------------------
CREATE TABLE parameter_config (
    parameter_key   VARCHAR(120) PRIMARY KEY,
    parameter_value TEXT NOT NULL,
    value_type      VARCHAR(20) NOT NULL,
    description     TEXT NOT NULL,
    updated_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_parameter_value_type CHECK (
        value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'JSON')
    )
);

CREATE TABLE parameter_change_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parameter_key       VARCHAR(120) NOT NULL REFERENCES parameter_config(parameter_key) ON DELETE RESTRICT,
    old_value           TEXT NOT NULL,
    new_value           TEXT NOT NULL,
    changed_by_user_id  BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    changed_by_username VARCHAR(80) NOT NULL,
    reason              TEXT,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parameter_change_log_key_time ON parameter_change_log(parameter_key, changed_at DESC);

INSERT INTO parameter_config(parameter_key, parameter_value, value_type, description) VALUES
('PLAZO_RADICACION_DIAS', '90', 'INTEGER', 'Máximo de días calendario entre fecha de servicio y fecha de radicación (R07).'),
('UMBRAL_PRIORIDAD_ALTA', '10000000', 'DECIMAL', 'Monto desde el cual un caso manual es prioridad ALTA.'),
('MONTO_AUTO_BASICO', '1500000', 'DECIMAL', 'Monto máximo automático inicial del plan BÁSICO; debe sincronizarse con la referencia del plan al cambiarse.'),
('MONTO_AUTO_CLASICO', '3000000', 'DECIMAL', 'Monto máximo automático inicial del plan CLÁSICO; debe sincronizarse con la referencia del plan al cambiarse.'),
('MONTO_AUTO_GLOBAL', '6000000', 'DECIMAL', 'Monto máximo automático inicial del plan GLOBAL; debe sincronizarse con la referencia del plan al cambiarse.'),
('ANS_AUTOMATICO_DIAS', '2', 'INTEGER', 'Días hábiles para los casos automáticos.'),
('ANS_ALTA_DIAS', '1', 'INTEGER', 'Días hábiles para casos manuales de prioridad ALTA.'),
('ANS_MEDIA_DIAS', '3', 'INTEGER', 'Días hábiles para casos manuales de prioridad MEDIA.'),
('ANS_BAJA_DIAS', '5', 'INTEGER', 'Días hábiles para casos manuales de prioridad BAJA.'),
('PALABRAS_R17', '["choque", "tránsito", "atropello", "moto", "andamio", "obra", "trabajo", "laboral", "agresión", "terceros", "rinoplastia", "estético", "liposucción"]', 'JSON', 'Lista base de términos para R17. El motor de reglas debe interpretar negaciones y no decidir por una palabra aislada.'),
('URL_API_FESTIVOS', 'https://date.nager.at/api/v3/PublicHolidays/{anio}/CO', 'STRING', 'API pública de festivos colombianos; se consume desde el backend.'),
('TIMEOUT_API_SEGUNDOS', '5', 'INTEGER', 'Tiempo máximo de espera de la API de festivos.'),
('FECHA_REFERENCIA', '', 'DATE', 'Fecha usada para semáforo ANS; vacía significa la fecha actual. La demo debe usar 2026-10-14.'),
('OBSERVACION_MIN_CARACTERES', '20', 'INTEGER', 'Longitud mínima de la observación de aprobación u objeción.'),
('TAMANO_MAX_ARCHIVO_MB', '5', 'INTEGER', 'Tamaño máximo del archivo Excel de entrada.'),
('CORS_ORIGINS', 'http://localhost:4200', 'STRING', 'Origen frontend permitido en desarrollo; cambiar para el despliegue correspondiente.'),
('RATE_LIMIT_REQUESTS', '120', 'INTEGER', 'Límite inicial orientativo de peticiones por ventana por cliente; se aplica en la capa API.')
ON CONFLICT (parameter_key) DO NOTHING;

-- --------------------------------------------------------------------------
-- 10. Lotes de pago y sus casos.
-- La confirmacion es simulada y solo la puede realizar ADMIN en la capa API.
-- Cada caso puede incorporarse una sola vez a un lote.
-- --------------------------------------------------------------------------
CREATE TABLE payment_batch (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_code              VARCHAR(60) NOT NULL UNIQUE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'EN_LOTE_PAGO',
    item_count              INTEGER NOT NULL DEFAULT 0 CHECK (item_count >= 0),
    total_amount            NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    generated_by_user_id    BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    confirmed_by_user_id    BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    generated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at                 TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    notes                   TEXT,
    CONSTRAINT ck_payment_batch_status CHECK (status IN ('EN_LOTE_PAGO', 'PAGADO', 'CERRADO'))
);

CREATE INDEX idx_payment_batch_status_date ON payment_batch(status, generated_at DESC);

CREATE TABLE payment_batch_item (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_batch_id    BIGINT NOT NULL REFERENCES payment_batch(id) ON DELETE RESTRICT,
    claim_case_id       BIGINT NOT NULL UNIQUE REFERENCES claim_case(id) ON DELETE RESTRICT,
    case_code_snapshot  VARCHAR(50) NOT NULL,
    amount_snapshot     NUMERIC(16,2) NOT NULL CHECK (amount_snapshot >= 0),
    added_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_batch_case UNIQUE (payment_batch_id, claim_case_id)
);

CREATE INDEX idx_payment_batch_item_batch ON payment_batch_item(payment_batch_id);

-- --------------------------------------------------------------------------
-- 11. Cache local de festivos colombianos.
-- El backend consulta Nager.Date una vez por año (o al renovar cache), conserva
-- los resultados y usa este cache si el servicio externo no responde.
-- --------------------------------------------------------------------------
CREATE TABLE holiday_cache (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    country_code    CHAR(2) NOT NULL DEFAULT 'CO',
    holiday_date    DATE NOT NULL,
    local_name      VARCHAR(200),
    english_name    VARCHAR(200),
    api_year        INTEGER NOT NULL,
    source_url      TEXT NOT NULL,
    retrieved_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_holiday_country_date UNIQUE (country_code, holiday_date),
    CONSTRAINT ck_holiday_country CHECK (country_code = 'CO'),
    CONSTRAINT ck_holiday_year CHECK (api_year BETWEEN 2000 AND 2200)
);

CREATE INDEX idx_holiday_year_date ON holiday_cache(api_year, holiday_date);

-- --------------------------------------------------------------------------
-- 12. Bitacora general de auditoria.
-- Guardar quien hizo que, cuando y con que resultado. No escribir contrasenas,
-- tokens JWT, documentos completos ni filas enteras del Excel en details.
-- --------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id       BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    actor_username      VARCHAR(80) NOT NULL DEFAULT 'SISTEMA',
    action_code         VARCHAR(100) NOT NULL,
    resource_type       VARCHAR(100),
    resource_id         VARCHAR(120),
    result              VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    details             JSONB NOT NULL DEFAULT '{}'::JSONB,
    correlation_id      VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_log_result CHECK (result IN ('OK', 'RECHAZADO', 'ERROR')),
    CONSTRAINT ck_audit_log_details_json CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_actor_date ON audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action_date ON audit_log(action_code, created_at DESC);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);

COMMENT ON TABLE audit_log IS 'Bitacora de seguridad y operaciones. Los detalles deben ser resumidos y no contener secretos ni datos personales sin necesidad.';

-- --------------------------------------------------------------------------
-- Fin de la migracion inicial.
-- Importante: la base de datos queda con el esquema listo, pero los casos y las
-- referencias del Excel se insertan mediante el importador de la aplicacion.
-- Los usuarios se crean con hashes BCrypt; nunca se insertan contrasenas planas.
-- --------------------------------------------------------------------------
