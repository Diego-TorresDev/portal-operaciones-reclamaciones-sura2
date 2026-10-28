-- Parámetro de negocio para elegir cómo se evalúa R17.
-- TEXTOS conserva el comportamiento vigente al aplicar esta migración.
-- La API de RugIA solo se invoca cuando ADMIN selecciona IA o AMBAS.
INSERT INTO parameter_config (
    parameter_key,
    parameter_value,
    value_type,
    description
)
VALUES (
    'R17_MODO_ANALISIS',
    'TEXTOS',
    'STRING',
    'Modo de análisis para R17: IA, TEXTOS o AMBAS. En AMBAS, una discrepancia o error de IA requiere revisión humana.'
)
ON CONFLICT (parameter_key) DO NOTHING;
