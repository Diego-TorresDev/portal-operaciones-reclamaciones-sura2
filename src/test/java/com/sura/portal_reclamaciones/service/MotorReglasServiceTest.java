package com.sura.portal_reclamaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.sura.portal_reclamaciones.dto.importacion.ResultadoEvaluacionLote;
import com.sura.portal_reclamaciones.entity.CaseEvaluation;
import com.sura.portal_reclamaciones.entity.CaseEvaluationRule;
import com.sura.portal_reclamaciones.entity.CaseTracking;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.entity.Diagnosis;
import com.sura.portal_reclamaciones.entity.InsurancePlan;
import com.sura.portal_reclamaciones.entity.ParameterConfig;
import com.sura.portal_reclamaciones.entity.Policy;
import com.sura.portal_reclamaciones.entity.Provider;
import com.sura.portal_reclamaciones.entity.RuleDefinition;
import com.sura.portal_reclamaciones.entity.ServiceCatalog;
import com.sura.portal_reclamaciones.repository.CaseEvaluationRepository;
import com.sura.portal_reclamaciones.repository.CaseEvaluationRuleRepository;
import com.sura.portal_reclamaciones.repository.CaseTrackingRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;
import com.sura.portal_reclamaciones.repository.DiagnosisRepository;
import com.sura.portal_reclamaciones.repository.InsurancePlanRepository;
import com.sura.portal_reclamaciones.repository.ParameterConfigRepository;
import com.sura.portal_reclamaciones.repository.PolicyRepository;
import com.sura.portal_reclamaciones.repository.ProviderRepository;
import com.sura.portal_reclamaciones.repository.RuleDefinitionRepository;
import com.sura.portal_reclamaciones.repository.ServiceCatalogRepository;

/**
 * Pruebas unitarias del flujo real del motor, con repositorios simulados.
 * No inicia Spring, no llama a Groq y no accede a PostgreSQL.
 */
class MotorReglasServiceTest {

    @Test
    void modoTextosDetectaChoqueYMotoYNoLlamaARugia() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "TEXTOS");
        f.caso.setObservations("Paciente llega tras choque en moto en la autopista.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertNotNull(r17);
        assertFalse(r17.isPassed());
        assertEquals("INCUMPLE", r17.getEvidence().get("clasificacionTextos"));
        assertEquals("SOLO_TEXTOS", r17.getEvidence().get("comparacion"));
        assertTrue(((List<?>) r17.getEvidence().get("terminosDetectados")).contains("choque"));
        assertTrue(((List<?>) r17.getEvidence().get("terminosDetectados")).contains("moto"));
        assertEquals(17, resultado.size(), "Cada evaluación debe guardar el resultado de las 17 reglas.");
        verify(f.rugia, never()).clasificarLote(any(), any(), anyString(), anyString());
    }

    @Test
    void modoTextosRespetaNegacionExplicitaDeChoqueYMoto() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "TEXTOS");
        f.caso.setObservations("Paciente niega choque en moto y descarta accidente de tránsito.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertTrue(r17.isPassed(), "Una negación explícita debe evitar marcar esos términos como exclusión.");
        assertTrue(((List<?>) r17.getEvidence().get("terminosDetectados")).isEmpty());
        assertEquals("SOLO_TEXTOS", r17.getEvidence().get("comparacion"));
    }

    @Test
    void modoIaUsaClasificacionDeRugiaYNoAprobaraAutomaticamentePorR17Sola() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "IA");
        f.caso.setObservations("Paciente llega tras choque en moto en la autopista.");
        f.respuestaRugia("CUMPLE", "OK", "El texto no indica una exclusión aplicable.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertTrue(r17.isPassed(), "En modo IA la decisión de R17 proviene de la respuesta válida de RugIA.");
        assertEquals("NO_EJECUTADO", r17.getEvidence().get("clasificacionTextos"));
        assertEquals("SOLO_IA", r17.getEvidence().get("comparacion"));
        assertEquals("MANUAL", f.caso.getCurrentDecision(),
                "Cumplir R17 no puede anular otras reglas incumplidas.");
        verify(f.rugia).clasificarLote(any(), any(), eq("IA"), eq("PRUEBA_AUTOMATIZADA"));
    }

    @Test
    void modoAmbasExigeRevisionSiLasClasificacionesDiscrepan() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "AMBAS");
        f.caso.setObservations("Paciente llega tras choque en moto en la autopista.");
        f.respuestaRugia("CUMPLE", "OK", "No encuentro evidencia de exclusión.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertFalse(r17.isPassed());
        assertEquals("DISCREPANCIA", r17.getEvidence().get("comparacion"));
        assertEquals("MANUAL", f.caso.getCurrentDecision());
        assertTrue(f.caso.getCurrentState().equals("EN_BANDEJA"));
    }

    @Test
    void modoIaConRespuestaNoDisponibleFallaR17DeFormaConservadora() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "IA");
        f.caso.setObservations("Observación de prueba sin una clasificación confirmada.");
        when(f.rugia.clasificarLote(any(), any(), eq("IA"), eq("PRUEBA_AUTOMATIZADA")))
                .thenReturn(Map.of());

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertFalse(r17.isPassed());
        assertEquals("REQUIERE_REVISION_IA_NO_DISPONIBLE", r17.getEvidence().get("comparacion"));
        assertEquals("MANUAL", f.caso.getCurrentDecision());
    }

    @Test
    void evaluacionGuardaCatalogoCompletoYReglasDeDatosInsuficientesNoAprueban() throws Exception {
        Fixture f = new Fixture();
        f.caso.setObservations("Observación sin exclusiones.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertEquals(Set.of(
                "R01", "R02", "R03", "R04", "R05", "R06", "R07", "R08", "R09",
                "R10", "R11", "R12", "R13", "R14", "R15", "R16", "R17"
        ), resultado.keySet());
        assertFalse(resultado.get("R01").isPassed(), "Los campos obligatorios incompletos deben fallar R01.");
        assertFalse(resultado.get("R02").isPassed(), "Sin fechas no se puede declarar coherencia temporal.");
        assertFalse(resultado.get("R04").isPassed(), "Sin catálogo de pólizas no se valida su existencia.");
        assertTrue(resultado.get("R09").isPassed(), "Un prestador ausente no equivale por sí mismo a una alerta de fraude.");
        assertTrue(resultado.get("R13").isPassed(), "Un diagnóstico ausente no debe inventar una exigencia de revisión médica en R13.");
        assertEquals("MANUAL", f.caso.getCurrentDecision());
    }

    // RUGIA_EXTRA_TESTS_V1

    @Test
    void modoAmbasConDosClasificacionesIncumpleCoincidenYQuedaManual() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "AMBAS");
        f.caso.setObservations("Paciente llega tras choque en moto en la autopista.");
        f.respuestaRugia("INCUMPLE", "OK", "Indica accidente de transito.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        CaseEvaluationRule r17 = resultado.get("R17");

        assertFalse(r17.isPassed());
        assertEquals("COINCIDEN_INCUMPLE", r17.getEvidence().get("comparacion"));
        assertEquals("MANUAL", f.caso.getCurrentDecision());
    }

    @Test
    void modoAmbasSiRugiaNoRespondeRequiereRevision() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "AMBAS");
        f.caso.setObservations("Consulta de prueba sin terminos de exclusion.");
        when(f.rugia.clasificarLote(any(), any(), eq("AMBAS"), eq("PRUEBA_AUTOMATIZADA")))
                .thenReturn(Map.of());

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R17").isPassed());
        assertEquals("REQUIERE_REVISION_IA_NO_DISPONIBLE",
                resultado.get("R17").getEvidence().get("comparacion"));
        assertEquals("MANUAL", f.caso.getCurrentDecision());
    }

    @Test
    void modoInvalidoVuelveATextosYSinInvocarRugia() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "MODO_DESCONOCIDO");
        f.caso.setObservations("Paciente llega tras choque en moto.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R17").isPassed());
        assertEquals("TEXTOS", resultado.get("R17").getEvidence().get("modoAnalisis"));
        assertEquals("SOLO_TEXTOS", resultado.get("R17").getEvidence().get("comparacion"));
        verify(f.rugia, never()).clasificarLote(any(), any(), anyString(), anyString());
    }

    @Test
    void modoTextosNormalizaTildesYMayusculasEnR17() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "TEXTOS");
        f.caso.setObservations("El paciente reporta AGRESI\u00D3N atribuida a TERCEROS.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();
        List<?> detectadas = (List<?>) resultado.get("R17").getEvidence().get("terminosDetectados");

        assertFalse(resultado.get("R17").isPassed());
        assertTrue(detectadas.contains("agresion"));
        assertTrue(detectadas.contains("terceros"));
    }

    @Test
    void modoTextosRespetaNegacionNoRefiereAccidenteDeTransito() throws Exception {
        Fixture f = new Fixture();
        f.parametrosValores.put("R17_MODO_ANALISIS", "TEXTOS");
        f.caso.setObservations("El paciente no refiere accidente de transito.");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertTrue(resultado.get("R17").isPassed());
        assertTrue(((List<?>) resultado.get("R17").getEvidence().get("terminosDetectados")).isEmpty());
    }

    @Test
    void casoCerradoNoSeReevaluaNiInvocaRugia() throws Exception {
        Fixture f = new Fixture();
        f.caso.setCurrentState("CERRADO");
        f.parametrosValores.put("R17_MODO_ANALISIS", "IA");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertTrue(resultado.isEmpty());
        verify(f.rugia, never()).clasificarLote(any(), any(), anyString(), anyString());
        verify(f.evaluaciones, never()).saveAndFlush(any(CaseEvaluation.class));
        verify(f.resultadosRegla, never()).saveAll(Mockito.<Iterable<CaseEvaluationRule>>any());
    }

    @Test
    void catalogoIncompletoDetieneEvaluacionAntesDeConsultarCasos() throws Exception {
        Fixture f = new Fixture();
        List<RuleDefinition> incompleto = new ArrayList<>(Fixture.catalogoCompleto());
        incompleto.remove(incompleto.size() - 1);
        when(f.reglas.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(incompleto);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> f.motor.evaluarPendientes("PRUEBA_AUTOMATIZADA")
        );

        verify(f.casos, never()).findAll();
        verify(f.evaluaciones, never()).saveAndFlush(any(CaseEvaluation.class));
    }

    @Test
    void reglaR03MarcaElSegundoCasoConLaMismaClaveComoDuplicado() throws Exception {
        Fixture f = new Fixture();
        f.caso.setImportBatchId(77L);
        f.caso.setNormalizedPolicyNumber("POL-DUP");
        f.caso.setNormalizedProviderNit("900123");
        f.caso.setServiceDate(java.time.LocalDate.of(2026, 10, 1));
        f.caso.setClaimAmount(new java.math.BigDecimal("100000"));

        ClaimCase segundo = new ClaimCase();
        Fixture.establecerCampo(segundo, "id", 2L);
        segundo.setCaseCode("CASO-TEST-DUP-002");
        segundo.setImportBatchId(77L);
        segundo.setNormalizedPolicyNumber("POL-DUP");
        segundo.setNormalizedProviderNit("900123");
        segundo.setServiceDate(java.time.LocalDate.of(2026, 10, 1));
        segundo.setClaimAmount(new java.math.BigDecimal("100000"));

        when(f.casos.findAll()).thenAnswer(invocation -> new ArrayList<>(List.of(f.caso, segundo)));

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R03").isPassed(),
                "El segundo caso con igual lote, poliza, NIT, fecha y valor debe marcarse duplicado.");
        assertEquals(34, f.reglasGuardadas.size(), "Se deben persistir las 17 reglas de cada caso procesado.");
    }

    @Test
    void reglasR10R11YR14CumplenConServicioActivoYSoportePresente() throws Exception {
        Fixture f = new Fixture();
        CatalogosPrueba catalogos = configurarCatalogosValidos(f);
        catalogos.servicio().setRequiredSupports(List.of("Factura_Adjunta"));
        f.caso.getSupportsPayload().put("Factura_Adjunta", "S");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertTrue(resultado.get("R10").isPassed());
        assertTrue(resultado.get("R11").isPassed());
        assertTrue(resultado.get("R14").isPassed());
    }

    @Test
    void reglaR11FallaCuandoFaltaUnSoporteObligatorio() throws Exception {
        Fixture f = new Fixture();
        CatalogosPrueba catalogos = configurarCatalogosValidos(f);
        catalogos.servicio().setRequiredSupports(List.of("Factura_Adjunta"));

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R11").isPassed());
        assertTrue(((List<?>) resultado.get("R11").getEvidence().get("soportesFaltantes"))
                .contains("factura"));
    }

    @Test
    void reglasR12YR13FallanSiDiagnosticoEstaExcluidoYExigeRevision() throws Exception {
        Fixture f = new Fixture();
        CatalogosPrueba catalogos = configurarCatalogosValidos(f);
        catalogos.diagnostico().setExcluded(true);
        catalogos.diagnostico().setRequiresMedicalReview(true);

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R12").isPassed());
        assertFalse(resultado.get("R13").isPassed());
        assertEquals("ALTA", f.caso.getCurrentPriority());
    }

    @Test
    void reglasR15YR16AceptanExactamenteElLimiteDelPlanYTopeAnual() throws Exception {
        Fixture f = new Fixture();
        configurarCatalogosValidos(f);
        f.parametrosValores.put("MONTO_AUTO_BASICO", "1500000");

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertTrue(resultado.get("R04").isPassed());
        assertTrue(resultado.get("R05").isPassed());
        assertTrue(resultado.get("R06").isPassed());
        assertTrue(resultado.get("R08").isPassed());
        assertTrue(resultado.get("R09").isPassed());
        assertTrue(resultado.get("R10").isPassed());
        assertTrue(resultado.get("R11").isPassed());
        assertTrue(resultado.get("R12").isPassed());
        assertTrue(resultado.get("R13").isPassed());
        assertTrue(resultado.get("R14").isPassed());
        assertTrue(resultado.get("R15").isPassed(), "La igualdad con el monto maximo automatico debe cumplir.");
        assertTrue(resultado.get("R16").isPassed(), "La igualdad con el tope anual debe cumplir.");
    }

    @Test
    void reglaR06FallaSiElServicioOcurreFueraDeLaVigencia() throws Exception {
        Fixture f = new Fixture();
        CatalogosPrueba catalogos = configurarCatalogosValidos(f);
        catalogos.poliza().setStartDate(java.time.LocalDate.of(2026, 11, 1));

        Map<String, CaseEvaluationRule> resultado = f.evaluar();

        assertFalse(resultado.get("R06").isPassed());
    }

    private record CatalogosPrueba(
            Policy poliza,
            InsurancePlan plan,
            Provider prestador,
            Diagnosis diagnostico,
            ServiceCatalog servicio) { }

    private static CatalogosPrueba configurarCatalogosValidos(Fixture f) {
        Policy poliza = new Policy();
        poliza.setPolicyNumber("POL-TEST-01");
        poliza.setStatus("VIGENTE");
        poliza.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        poliza.setEndDate(java.time.LocalDate.of(2026, 12, 31));
        poliza.setAnnualConsumedAmount(new java.math.BigDecimal("500000"));

        InsurancePlan plan = new InsurancePlan();
        plan.setPlanCode("BASICO");
        plan.setPlanName("Plan basico de prueba");
        plan.setMaxAutoApprovalAmount(new java.math.BigDecimal("1500000"));
        plan.setAnnualCap(new java.math.BigDecimal("2000000"));
        poliza.setPlan(plan);

        Provider prestador = new Provider();
        prestador.setNit("900123");
        prestador.setInNetwork(true);
        prestador.setFraudAlert(false);

        Diagnosis diagnostico = new Diagnosis();
        diagnostico.setDiagnosisCode("DX-TEST");
        diagnostico.setExcluded(false);
        diagnostico.setRequiresMedicalReview(false);

        ServiceCatalog servicio = new ServiceCatalog();
        servicio.setServiceCode("CONSULTA");
        servicio.setServiceName("Consulta de prueba");
        servicio.setActive(true);
        servicio.setRequiredSupports(List.of());

        f.caso.setDocumentNumber("12345678");
        f.caso.setImportBatchId(50L);
        f.caso.setNormalizedPolicyNumber("POL-TEST-01");
        f.caso.setNormalizedProviderNit("900123");
        f.caso.setNormalizedDiagnosisCode("DX-TEST");
        f.caso.setNormalizedServiceType("CONSULTA");
        f.caso.setClaimAmount(new java.math.BigDecimal("1500000"));
        f.caso.setServiceDate(java.time.LocalDate.of(2026, 10, 1));
        // filingDate se deja nula: R01/R02/R07 deben fallar de forma conservadora
        // y el test no necesita invocar el calendario de festivos.

        when(f.polizas.findByPolicyNumberIgnoreCase(anyString())).thenReturn(Optional.of(poliza));
        when(f.prestadores.findByNit(anyString())).thenReturn(Optional.of(prestador));
        when(f.diagnosticos.findByDiagnosisCodeIgnoreCase(anyString())).thenReturn(Optional.of(diagnostico));
        when(f.servicios.findByServiceCodeIgnoreCase(anyString())).thenReturn(Optional.of(servicio));

        return new CatalogosPrueba(poliza, plan, prestador, diagnostico, servicio);
    }
    private static final class Fixture {
        private final ClaimCaseRepository casos = mock(ClaimCaseRepository.class);
        private final RuleDefinitionRepository reglas = mock(RuleDefinitionRepository.class);
        private final CaseEvaluationRepository evaluaciones = mock(CaseEvaluationRepository.class);
        private final CaseEvaluationRuleRepository resultadosRegla = mock(CaseEvaluationRuleRepository.class);
        private final CaseTrackingRepository tracking = mock(CaseTrackingRepository.class);
        private final ParameterConfigRepository parametros = mock(ParameterConfigRepository.class);
        private final PolicyRepository polizas = mock(PolicyRepository.class);
        private final InsurancePlanRepository planes = mock(InsurancePlanRepository.class);
        private final ProviderRepository prestadores = mock(ProviderRepository.class);
        private final DiagnosisRepository diagnosticos = mock(DiagnosisRepository.class);
        private final ServiceCatalogRepository servicios = mock(ServiceCatalogRepository.class);
        private final FestivosColombiaService festivos = mock(FestivosColombiaService.class);
        private final RugiaService rugia = mock(RugiaService.class);
        private final Map<String, String> parametrosValores = new LinkedHashMap<>();
        private final List<CaseEvaluationRule> reglasGuardadas = new ArrayList<>();
        private final AtomicLong siguienteEvaluacionId = new AtomicLong(100);
        private final ClaimCase caso = new ClaimCase();
        private final MotorReglasService motor;

        private Fixture() throws Exception {
            establecerCampo(caso, "id", 1L);
            caso.setCaseCode("CASO-TEST-RULES-001");
            caso.setCurrentState("RADICADO");
            caso.setObservations("Observación de prueba.");
            // Se deja filingDate nula para que la prueba no invoque el calendario externo.

            parametrosValores.put("R17_MODO_ANALISIS", "TEXTOS");
            parametrosValores.put("PALABRAS_R17",
                    "[\"choque\", \"tránsito\", \"atropello\", \"moto\", \"andamio\", \"obra\", \"trabajo\", \"laboral\", \"agresión\", \"terceros\", \"rinoplastia\", \"estético\", \"liposucción\"]");
            parametrosValores.put("FECHA_REFERENCIA", "2026-10-14");

            when(reglas.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(catalogoCompleto());
            when(casos.findAll()).thenAnswer(invocation -> new ArrayList<>(List.of(caso)));
            when(polizas.findByPolicyNumberIgnoreCase(anyString())).thenReturn(Optional.<Policy>empty());
            when(prestadores.findByNit(anyString())).thenReturn(Optional.<Provider>empty());
            when(diagnosticos.findByDiagnosisCodeIgnoreCase(anyString())).thenReturn(Optional.<Diagnosis>empty());
            when(servicios.findByServiceCodeIgnoreCase(anyString())).thenReturn(Optional.<ServiceCatalog>empty());
            when(evaluaciones.findByClaimCaseIdAndCurrentTrue(anyLong())).thenReturn(Optional.<CaseEvaluation>empty());
            when(evaluaciones.countByClaimCaseId(anyLong())).thenReturn(0L);
            when(evaluaciones.saveAndFlush(any(CaseEvaluation.class))).thenAnswer(invocation -> {
                CaseEvaluation evaluacion = invocation.getArgument(0);
                establecerCampo(evaluacion, "id", siguienteEvaluacionId.getAndIncrement());
                return evaluacion;
            });
            when(resultadosRegla.saveAll(Mockito.<Iterable<CaseEvaluationRule>>any())).thenAnswer(invocation -> {
                Iterable<CaseEvaluationRule> entidades = invocation.getArgument(0);
                List<CaseEvaluationRule> copia = new ArrayList<>();
                entidades.forEach(copia::add);
                reglasGuardadas.addAll(copia);
                return copia;
            });
            when(parametros.findById(anyString())).thenAnswer(invocation -> {
                String clave = invocation.getArgument(0);
                String valor = parametrosValores.get(clave);
                return valor == null ? Optional.<ParameterConfig>empty()
                        : Optional.of(parametro(clave, valor));
            });
            when(parametros.findAll()).thenAnswer(invocation -> parametrosValores.entrySet().stream()
                    .map(entrada -> parametro(entrada.getKey(), entrada.getValue()))
                    .toList());

            motor = new MotorReglasService(
                    casos, reglas, evaluaciones, resultadosRegla, tracking, parametros,
                    polizas, planes, prestadores, diagnosticos, servicios, festivos, rugia
            );
        }

        private Map<String, CaseEvaluationRule> evaluar() {
            ResultadoEvaluacionLote resumen = motor.evaluarPendientes("PRUEBA_AUTOMATIZADA");
            assertNotNull(resumen);
            return reglasGuardadas.stream().collect(Collectors.toMap(
                    CaseEvaluationRule::getRuleCode,
                    regla -> regla,
                    (primera, segunda) -> segunda,
                    LinkedHashMap::new
            ));
        }

        private void respuestaRugia(String clasificacion, String estado, String explicacion) {
            RugiaService.ResultadoR17 respuesta = new RugiaService.ResultadoR17(
                    clasificacion,
                    "",
                    explicacion,
                    estado,
                    "modelo-prueba"
            );
            when(rugia.clasificarLote(any(), any(), anyString(), anyString()))
                    .thenReturn(Map.of(0, respuesta));
        }

        private static ParameterConfig parametro(String clave, String valor) {
            ParameterConfig p = new ParameterConfig();
            p.setParameterKey(clave);
            p.setParameterValue(valor);
            p.setValueType("STRING");
            p.setDescription("Parámetro simulado para prueba unitaria");
            return p;
        }

        private static List<RuleDefinition> catalogoCompleto() {
            List<String> codigos = List.of(
                    "R01", "R02", "R03", "R04", "R05", "R06", "R07", "R08", "R09",
                    "R10", "R11", "R12", "R13", "R14", "R15", "R16", "R17"
            );
            List<RuleDefinition> lista = new ArrayList<>();
            short orden = 1;
            for (String codigo : codigos) {
                RuleDefinition regla = new RuleDefinition();
                regla.setRuleCode(codigo);
                regla.setRuleName(codigo + " de prueba");
                regla.setDescription("Definición de prueba");
                regla.setBasePriority("MEDIA");
                regla.setActive(true);
                regla.setDisplayOrder(orden++);
                lista.add(regla);
            }
            return lista;
        }

        private static void establecerCampo(Object objeto, String nombre, Object valor) {
            try {
                Field campo = objeto.getClass().getDeclaredField(nombre);
                campo.setAccessible(true);
                campo.set(objeto, valor);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("No fue posible preparar la entidad de prueba.", error);
            }
        }
    }
}
