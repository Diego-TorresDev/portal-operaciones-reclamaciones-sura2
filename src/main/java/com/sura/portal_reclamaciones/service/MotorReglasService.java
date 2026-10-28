package com.sura.portal_reclamaciones.service;

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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// El motor concentra las reglas para que los controladores no decidan pagos.
@Service
public class MotorReglasService {

    private static final List<String> CODIGOS_REGLA = List.of(
            "R01", "R02", "R03", "R04", "R05", "R06", "R07",
            "R08", "R09", "R10", "R11", "R12", "R13", "R14",
            "R15", "R16", "R17"
    );

    private static final Set<String> REGLAS_PRIORIDAD_ALTA =
            Set.of("R09", "R13", "R16");

    private static final Set<String> REGLAS_QUE_PERMITEN_PRIORIDAD_BAJA =
            Set.of("R01", "R02", "R11");

    private final ClaimCaseRepository casos;
    private final RuleDefinitionRepository reglas;
    private final CaseEvaluationRepository evaluaciones;
    private final CaseEvaluationRuleRepository resultadosRegla;
    private final CaseTrackingRepository tracking;
    private final ParameterConfigRepository parametros;
    private final PolicyRepository polizas;
    private final InsurancePlanRepository planes;
    private final ProviderRepository prestadores;
    private final DiagnosisRepository diagnosticos;
    private final ServiceCatalogRepository servicios;
    private final FestivosColombiaService festivosColombiaService;
    private final RugiaService rugiaService;

    public MotorReglasService(
            ClaimCaseRepository casos,
            RuleDefinitionRepository reglas,
            CaseEvaluationRepository evaluaciones,
            CaseEvaluationRuleRepository resultadosRegla,
            CaseTrackingRepository tracking,
            ParameterConfigRepository parametros,
            PolicyRepository polizas,
            InsurancePlanRepository planes,
            ProviderRepository prestadores,
            DiagnosisRepository diagnosticos,
            ServiceCatalogRepository servicios,
            FestivosColombiaService festivosColombiaService,
            RugiaService rugiaService
    ) {
        this.casos = casos;
        this.reglas = reglas;
        this.evaluaciones = evaluaciones;
        this.resultadosRegla = resultadosRegla;
        this.tracking = tracking;
        this.parametros = parametros;
        this.polizas = polizas;
        this.planes = planes;
        this.prestadores = prestadores;
        this.diagnosticos = diagnosticos;
        this.servicios = servicios;
        this.festivosColombiaService = festivosColombiaService;
        this.rugiaService = rugiaService;
    }

    /** Compatibilidad con llamadas internas que no aportan un usuario. */
    @Transactional
    public ResultadoEvaluacionLote evaluarPendientes() {
        return evaluarPendientes("SISTEMA");
    }

    /** Ejecuta el motor y, cuando corresponda, una única consulta por lote a RugIA. */
    @Transactional
    public ResultadoEvaluacionLote evaluarPendientes(String usuarioSolicitante) {
        List<RuleDefinition> catalogo =
                reglas.findAllByActiveTrueOrderByDisplayOrderAsc();

        Set<String> codigosEncontrados = new LinkedHashSet<>();
        for (RuleDefinition regla : catalogo) {
            codigosEncontrados.add(regla.getRuleCode());
        }

        if (catalogo.size() != 17 || !codigosEncontrados.containsAll(CODIGOS_REGLA)) {
            throw new IllegalStateException(
                    "El catálogo debe tener las 17 reglas R01 a R17 activas."
            );
        }

        List<ClaimCase> todos = casos.findAll();
        todos.sort(Comparator
                .comparing((ClaimCase c) -> c.getImportBatchId() == null ? -1L : c.getImportBatchId())
                .thenComparing(c -> c.getSourceRowNumber() == null ? Integer.MAX_VALUE : c.getSourceRowNumber())
                .thenComparing(c -> c.getId() == null ? 0L : c.getId()));

        Map<Long, Boolean> sonDuplicados = identificarDuplicados(todos);
        List<ClaimCase> elegibles = todos.stream()
                .filter(c -> esEstadoReevaluable(c.getCurrentState()))
                .toList();

        String modoR17 = modoAnalisisR17();
        Map<Integer, RugiaService.ResultadoR17> resultadosRugia = new LinkedHashMap<>();

        // RugIA solo se invoca desde este flujo de evaluación/reevaluación,
        // nunca durante la consulta de un detalle ni desde el renderizado web.
        // TEXTOS mantiene el comportamiento vigente y no consume llamadas de IA.
        if (!elegibles.isEmpty() && !"TEXTOS".equals(modoR17)) {
            List<String> observaciones = elegibles.stream()
                    .map(ClaimCase::getObservations)
                    .toList();
            resultadosRugia = rugiaService.clasificarLote(
                    observaciones,
                    palabrasRiesgoConfiguradas(),
                    modoR17,
                    usuarioSolicitante
            );
        }

        int automaticos = 0;
        int manuales = 0;
        List<String> errores = new ArrayList<>();

        for (int indice = 0; indice < elegibles.size(); indice++) {
            ClaimCase caso = elegibles.get(indice);
            try {
                boolean duplicado = sonDuplicados.getOrDefault(caso.getId(), false);
                RugiaService.ResultadoR17 resultadoRugia = resultadosRugia.get(indice);

                evaluarCaso(caso, catalogo, duplicado, modoR17, resultadoRugia);

                if ("AUTOMATICO".equals(caso.getCurrentDecision())) {
                    automaticos++;
                } else {
                    manuales++;
                }
            } catch (RuntimeException error) {
                throw new IllegalStateException(
                        "No se pudo evaluar el caso " + caso.getCaseCode()
                                + ". La operación se revertirá.", error
                );
            }
        }

        return new ResultadoEvaluacionLote(
                todos.size(), elegibles.size(), automaticos, manuales,
                todos.size() - elegibles.size(),
                "Evaluación finalizada. Cada caso procesado fue revisado contra las 17 reglas.",
                errores
        );
    }

    private Map<Long, Boolean> identificarDuplicados(List<ClaimCase> todos) {
        Set<String> clavesVistas = new HashSet<>();
        Map<Long, Boolean> resultado = new HashMap<>();

        for (ClaimCase caso : todos) {
            resultado.put(caso.getId(), false);
            if (caso.getImportBatchId() == null
                    || caso.getNormalizedPolicyNumber() == null
                    || caso.getNormalizedProviderNit() == null
                    || caso.getServiceDate() == null
                    || caso.getClaimAmount() == null) {
                continue;
            }

            String clave = caso.getImportBatchId() + "|"
                    + normalizarCodigo(caso.getNormalizedPolicyNumber()) + "|"
                    + normalizarCodigo(caso.getNormalizedProviderNit()) + "|"
                    + caso.getServiceDate() + "|"
                    + caso.getClaimAmount().stripTrailingZeros().toPlainString();

            if (!clavesVistas.add(clave)) {
                resultado.put(caso.getId(), true);
            }
        }
        return resultado;
    }

    private boolean esEstadoReevaluable(String estado) {
        return "RADICADO".equals(estado)
                || "APROBADO_AUTOMATICO".equals(estado)
                || "EN_BANDEJA".equals(estado);
    }

    private void evaluarCaso(
            ClaimCase caso,
            List<RuleDefinition> catalogo,
            boolean esDuplicado,
            String modoR17,
            RugiaService.ResultadoR17 resultadoRugia
    ) {
        Policy poliza = polizas.findByPolicyNumberIgnoreCase(
                valorSeguro(caso.getNormalizedPolicyNumber())
        ).orElse(null);
        Provider prestador = prestadores.findByNit(
                valorSeguro(caso.getNormalizedProviderNit())
        ).orElse(null);
        Diagnosis diagnostico = diagnosticos.findByDiagnosisCodeIgnoreCase(
                valorSeguro(caso.getNormalizedDiagnosisCode())
        ).orElse(null);
        ServiceCatalog servicio = servicios.findByServiceCodeIgnoreCase(
                valorSeguro(caso.getNormalizedServiceType())
        ).orElse(null);
        InsurancePlan plan = poliza == null ? null : poliza.getPlan();

        evaluaciones.findByClaimCaseIdAndCurrentTrue(caso.getId())
                .ifPresent(anterior -> {
                    anterior.setCurrent(false);
                    evaluaciones.save(anterior);
                });

        int numeroEvaluacion = (int) evaluaciones.countByClaimCaseId(caso.getId()) + 1;
        Map<String, Object> fotoParametros = crearFotoParametros();

        CaseEvaluation evaluacion = new CaseEvaluation();
        evaluacion.setClaimCaseId(caso.getId());
        evaluacion.setEvaluationNumber(numeroEvaluacion);
        evaluacion.setCurrent(true);
        evaluacion.setParameterSnapshot(fotoParametros);

        List<String> reglasFallidas = new ArrayList<>();
        List<CaseEvaluationRule> detalleReglas = new ArrayList<>();

        for (RuleDefinition regla : catalogo) {
            String codigo = regla.getRuleCode();
            ResultadoRegla resultado = comprobarRegla(
                    codigo, caso, poliza, plan, prestador, diagnostico,
                    servicio, esDuplicado, modoR17, resultadoRugia
            );

            CaseEvaluationRule fila = new CaseEvaluationRule();
            fila.setRuleCode(codigo);
            fila.setPassed(resultado.cumple());
            fila.setBusinessExplanation(resultado.explicacion());
            fila.setEvidence(resultado.evidencia());
            detalleReglas.add(fila);
            if (!resultado.cumple()) {
                reglasFallidas.add(codigo);
            }
        }

        boolean automatico = reglasFallidas.isEmpty();
        String decision = automatico ? "AUTOMATICO" : "MANUAL";
        String prioridad = automatico ? null : calcularPrioridad(caso, reglasFallidas);
        String motivo = automatico
                ? "Cumple las 17 reglas de validación y puede continuar al pago automático."
                : "Requiere revisión manual. Reglas incumplidas: "
                        + String.join(", ", reglasFallidas) + ".";

        evaluacion.setDecision(decision);
        evaluacion.setPriority(prioridad);
        evaluacion.setBusinessReason(motivo);
        evaluacion.setFailedRuleCodes(new ArrayList<>(reglasFallidas));
        evaluacion.setFailedRuleCount((short) reglasFallidas.size());
        evaluacion = evaluaciones.saveAndFlush(evaluacion);

        for (CaseEvaluationRule fila : detalleReglas) {
            fila.setCaseEvaluationId(evaluacion.getId());
        }
        resultadosRegla.saveAll(detalleReglas);

        String estadoAnterior = caso.getCurrentState();
        if ("RADICADO".equals(estadoAnterior)) {
            registrarCambio(caso, "RADICADO", "VALIDADO", "SISTEMA",
                    "Datos normalizados antes de evaluar las reglas.");
            caso.setCurrentState("VALIDADO");
            registrarCambio(caso, "VALIDADO", "EVALUADO", "SISTEMA",
                    "Se aplicaron las 17 reglas de negocio.");
            caso.setCurrentState("EVALUADO");

            String destino = automatico ? "APROBADO_AUTOMATICO" : "EN_BANDEJA";
            registrarCambio(caso, "EVALUADO", destino, "SISTEMA", motivo);
            caso.setCurrentState(destino);
        } else {
            String destino = automatico ? "APROBADO_AUTOMATICO" : "EN_BANDEJA";
            if (!destino.equals(estadoAnterior)) {
                registrarCambio(caso, estadoAnterior, destino, "SISTEMA",
                        "Estado actualizado tras reevaluar las reglas.");
                caso.setCurrentState(destino);
            }
        }

        caso.setCurrentDecision(decision);
        caso.setCurrentPriority(prioridad);
        caso.setCurrentEvaluationId(evaluacion.getId());
        calcularAnsProvisional(caso, prioridad, automatico);
        casos.save(caso);
    }

    private ResultadoRegla comprobarRegla(
            String codigo,
            ClaimCase caso,
            Policy poliza,
            InsurancePlan plan,
            Provider prestador,
            Diagnosis diagnostico,
            ServiceCatalog servicio,
            boolean duplicado,
            String modoR17,
            RugiaService.ResultadoR17 resultadoRugia
    ) {
        return switch (codigo) {
            case "R01" -> regla(
                    caso.getDocumentNumber() != null
                            && !caso.getDocumentNumber().isBlank()
                            && caso.getNormalizedPolicyNumber() != null
                            && !caso.getNormalizedPolicyNumber().isBlank()
                            && caso.getClaimAmount() != null
                            && caso.getClaimAmount().compareTo(BigDecimal.ZERO) > 0
                            && caso.getServiceDate() != null
                            && caso.getFilingDate() != null,
                    "Debe tener documento, póliza, valor positivo y ambas fechas válidas.",
                    "Los datos obligatorios están diligenciados."
            );

            case "R02" -> regla(
                    caso.getServiceDate() != null
                            && caso.getFilingDate() != null
                            && !caso.getServiceDate().isAfter(caso.getFilingDate()),
                    "La fecha del servicio es posterior a la fecha de radicación o no se pudo validar.",
                    "La fecha del servicio no es posterior a la radicación."
            );

            case "R03" -> regla(
                    !duplicado,
                    "Ya existe una reclamación anterior con la misma póliza, NIT, fecha de servicio y valor.",
                    "No se encontró una reclamación anterior con los mismos datos."
            );

            case "R04" -> regla(
                    poliza != null,
                    "La póliza indicada no existe en el catálogo de pólizas.",
                    "La póliza existe en el catálogo."
            );

            case "R05" -> regla(
                    poliza != null && "VIGENTE".equals(normalizarCodigo(poliza.getStatus())),
                    "La póliza no está en estado Vigente.",
                    "La póliza está vigente."
            );

            case "R06" -> regla(
                    poliza != null
                            && poliza.getStartDate() != null
                            && poliza.getEndDate() != null
                            && caso.getServiceDate() != null
                            && !caso.getServiceDate().isBefore(poliza.getStartDate())
                            && !caso.getServiceDate().isAfter(poliza.getEndDate()),
                    "La fecha del servicio está por fuera de la vigencia de la póliza.",
                    "La fecha del servicio está dentro de la vigencia de la póliza."
            );

            case "R07" -> {
                int plazo = enteroParametro("PLAZO_RADICACION_DIAS", 90);
                boolean cumple = caso.getServiceDate() != null && caso.getFilingDate() != null;
                if (cumple) {
                    long dias = ChronoUnit.DAYS.between(caso.getServiceDate(), caso.getFilingDate());
                    cumple = dias >= 0 && dias <= plazo;
                }
                yield regla(
                        cumple,
                        "La reclamación supera el plazo permitido de " + plazo + " días calendario.",
                        "La reclamación fue radicada dentro del plazo permitido."
                );
            }

            case "R08" -> regla(
                    prestador != null && prestador.isInNetwork(),
                    "El NIT del prestador no existe en la red contratada.",
                    "El prestador pertenece a la red contratada."
            );

            case "R09" -> regla(
                    prestador == null || !prestador.isFraudAlert(),
                    "El prestador tiene una alerta de fraude activa.",
                    "El prestador no tiene una alerta de fraude activa."
            );

            case "R10" -> regla(
                    servicio != null && servicio.isActive(),
                    "El tipo de servicio no existe o está inactivo en el catálogo.",
                    "El tipo de servicio existe en el catálogo."
            );

            case "R11" -> {
                List<String> soportesFaltantes = new ArrayList<>();
                if (servicio == null) {
                    soportesFaltantes.add("No se pudo consultar el catálogo de soportes");
                } else {
                    List<String> requeridos = servicio.getRequiredSupports();
                    if (requeridos != null) {
                        for (String soporte : requeridos) {
                            Object valor = caso.getSupportsPayload().get(soporte);
                            if (!esAfirmativo(valor)) {
                                soportesFaltantes.add(nombreSoporte(soporte));
                            }
                        }
                    }
                }
                yield new ResultadoRegla(
                        soportesFaltantes.isEmpty(),
                        soportesFaltantes.isEmpty()
                                ? "Todos los soportes requeridos están adjuntos."
                                : "Faltan soportes obligatorios: " + String.join(", ", soportesFaltantes) + ".",
                        Map.of("soportesFaltantes", soportesFaltantes)
                );
            }

            case "R12" -> regla(
                    diagnostico != null && !diagnostico.isExcluded(),
                    "El diagnóstico no existe en el catálogo o está excluido.",
                    "El diagnóstico existe y no está excluido."
            );

            case "R13" -> regla(
                    diagnostico == null || !diagnostico.isRequiresMedicalReview(),
                    "El diagnóstico requiere revisión médica.",
                    "El diagnóstico no requiere revisión médica."
            );

            case "R14" -> regla(
                    !normalizarTexto(caso.getNormalizedServiceType()).equals("hospitalizacion"),
                    "El servicio corresponde a Hospitalización, que no admite aprobación automática.",
                    "El servicio no corresponde a Hospitalización."
            );

            case "R15" -> {
                BigDecimal maximoAutomatico = obtenerMontoMaximoAutomatico(plan);
                yield regla(
                        caso.getClaimAmount() != null
                                && maximoAutomatico != null
                                && caso.getClaimAmount().compareTo(maximoAutomatico) <= 0,
                        "El valor reclamado supera el monto máximo de aprobación automática del plan o no se pudo validar.",
                        "El valor reclamado está dentro del límite de aprobación automática."
                );
            }

            case "R16" -> {
                boolean cumple = caso.getClaimAmount() != null
                        && poliza != null
                        && poliza.getAnnualConsumedAmount() != null
                        && plan != null
                        && plan.getAnnualCap() != null;
                if (cumple) {
                    BigDecimal totalAnual = poliza.getAnnualConsumedAmount().add(caso.getClaimAmount());
                    cumple = totalAnual.compareTo(plan.getAnnualCap()) <= 0;
                }
                yield regla(
                        cumple,
                        "El consumo anual de la póliza más esta reclamación supera el tope anual del plan o no se pudo validar.",
                        "La reclamación no supera el tope anual disponible del plan."
                );
            }

            case "R17" -> evaluarR17(caso.getObservations(), modoR17, resultadoRugia);

            default -> throw new IllegalArgumentException("Código de regla no reconocido: " + codigo);
        };
    }

    /**
     * Resuelve R17 según el modo de ADMIN y conserva en evidence tanto el
     * resultado textual como el de RugIA. Una discrepancia siempre falla R17,
     * dejando la reclamación en revisión manual; nunca rechaza el pago por sí sola.
     */
    private ResultadoRegla evaluarR17(
            String observaciones,
            String modo,
            RugiaService.ResultadoR17 resultadoRugia) {

        // En modo IA no ejecutamos el detector de palabras para tomar la decisión.
        List<String> detectadas = "IA".equals(modo)
                ? List.of() : palabrasRiesgoDetectadas(observaciones);
        boolean textoCumple = detectadas.isEmpty();
        String clasificacionTexto = textoCumple ? "CUMPLE" : "INCUMPLE";
        RugiaService.ResultadoR17 ia = resultadoRugia;
        if (ia == null) {
            ia = new RugiaService.ResultadoR17(
                    "INDETERMINADO", "",
                    "No se recibió un resultado válido de RugIA.",
                    "SIN_RESULTADO", ""
            );
        }

        boolean iaUtilizable = ("OK".equals(ia.estado()) || "SIN_OBSERVACION".equals(ia.estado()))
                && ("CUMPLE".equals(ia.clasificacion()) || "INCUMPLE".equals(ia.clasificacion()));
        boolean iaCumple = "CUMPLE".equals(ia.clasificacion());
        Map<String, Object> evidencia = new LinkedHashMap<>();
        evidencia.put("modoAnalisis", modo);
        evidencia.put("clasificacionTextos", "TEXTOS".equals(modo) ? clasificacionTexto : ("IA".equals(modo) ? "NO_EJECUTADO" : clasificacionTexto));
        evidencia.put("terminosDetectados", "IA".equals(modo) ? List.of() : detectadas);
        evidencia.put("clasificacionRugia", "TEXTOS".equals(modo) ? "NO_EJECUTADO" : ia.clasificacion());
        evidencia.put("estadoRugia", "TEXTOS".equals(modo) ? "NO_EJECUTADO" : ia.estado());
        evidencia.put("evidenciaRugia", "TEXTOS".equals(modo) ? "" : ia.evidencia());
        evidencia.put("explicacionRugia", "TEXTOS".equals(modo) ? "" : ia.explicacion());
        evidencia.put("modeloRugia", "TEXTOS".equals(modo) ? "" : ia.modelo());

        if ("TEXTOS".equals(modo)) {
            evidencia.put("comparacion", "SOLO_TEXTOS");
            return new ResultadoRegla(
                    textoCumple,
                    textoCumple
                            ? "El análisis textual no encontró indicios configurados para R17."
                            : "Las observaciones requieren revisión por estos términos: " + String.join(", ", detectadas) + ".",
                    evidencia
            );
        }

        if ("IA".equals(modo)) {
            evidencia.put("comparacion", iaUtilizable ? "SOLO_IA" : "REQUIERE_REVISION_IA_NO_DISPONIBLE");
            if (!iaUtilizable) {
                return new ResultadoRegla(
                        false,
                        "RugIA no pudo confirmar una clasificación válida de R17 (" + ia.estado() + "); requiere revisión humana.",
                        evidencia
                );
            }
            return new ResultadoRegla(
                    iaCumple,
                    iaCumple
                            ? "RugIA no identificó indicios que incumplan R17."
                            : "RugIA identificó indicios que incumplen R17: " + textoSeguroCorto(ia.evidencia()) + ".",
                    evidencia
            );
        }

        // En AMBAS, los resultados deben coincidir y la respuesta IA debe ser válida.
        if (!iaUtilizable) {
            evidencia.put("comparacion", "REQUIERE_REVISION_IA_NO_DISPONIBLE");
            return new ResultadoRegla(
                    false,
                    "No se pudo comparar de forma fiable el análisis textual con RugIA; R17 requiere revisión humana.",
                    evidencia
            );
        }

        if (textoCumple != iaCumple) {
            evidencia.put("comparacion", "DISCREPANCIA");
            return new ResultadoRegla(
                    false,
                    "Discrepancia en R17: el análisis textual clasificó " + clasificacionTexto
                            + " y RugIA clasificó " + ia.clasificacion()
                            + ". Se requiere revisión humana.",
                    evidencia
            );
        }

        evidencia.put("comparacion", textoCumple ? "COINCIDEN_CUMPLE" : "COINCIDEN_INCUMPLE");
        return new ResultadoRegla(
                textoCumple,
                textoCumple
                        ? "El análisis textual y RugIA coinciden: no se detectan indicios que incumplan R17."
                        : "El análisis textual y RugIA coinciden en que hay indicios que incumplen R17: "
                                + String.join(", ", detectadas) + (ia.evidencia().isBlank() ? "." : "; evidencia de RugIA: " + textoSeguroCorto(ia.evidencia()) + "."),
                evidencia
        );
    }

    private String textoSeguroCorto(String valor) {
        if (valor == null || valor.isBlank()) {
            return "evidencia no disponible";
        }
        String limpio = valor.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        return limpio.length() <= 180 ? limpio : limpio.substring(0, 180);
    }

    private String modoAnalisisR17() {
        String valor = textoParametro("R17_MODO_ANALISIS", "TEXTOS").trim().toUpperCase(Locale.ROOT);
        if (Set.of("IA", "TEXTOS", "AMBAS").contains(valor)) {
            return valor;
        }
        // Si el dato fue alterado fuera de los endpoints, se aplica el modo
        // textual ya existente para no habilitar aprobaciones por configuración inválida.
        return "TEXTOS";
    }

    private ResultadoRegla regla(
            boolean cumple,
            String explicacionFallo,
            String explicacionCumplimiento) {
        return new ResultadoRegla(cumple, cumple ? explicacionCumplimiento : explicacionFallo, Map.of());
    }

    private String calcularPrioridad(ClaimCase caso, List<String> fallidas) {
        BigDecimal umbral = decimalParametro("UMBRAL_PRIORIDAD_ALTA", new BigDecimal("10000000"));
        if (fallidas.stream().anyMatch(REGLAS_PRIORIDAD_ALTA::contains)
                || (caso.getClaimAmount() != null && caso.getClaimAmount().compareTo(umbral) > 0)) {
            return "ALTA";
        }
        if (fallidas.stream().allMatch(REGLAS_QUE_PERMITEN_PRIORIDAD_BAJA::contains)) {
            return "BAJA";
        }
        return "MEDIA";
    }

    private BigDecimal obtenerMontoMaximoAutomatico(InsurancePlan plan) {
        if (plan == null) return null;
        String codigoPlan = normalizarTexto(plan.getPlanCode()).replaceAll("[^a-z0-9]", "");
        String claveParametro = switch (codigoPlan) {
            case "basico" -> "MONTO_AUTO_BASICO";
            case "clasico" -> "MONTO_AUTO_CLASICO";
            case "global" -> "MONTO_AUTO_GLOBAL";
            default -> null;
        };
        if (claveParametro == null) return plan.getMaxAutoApprovalAmount();
        String valorConfigurado = textoParametro(claveParametro, "");
        if (valorConfigurado.isBlank()) return plan.getMaxAutoApprovalAmount();
        BigDecimal montoConfigurado = convertirMontoConfigurado(valorConfigurado);
        return montoConfigurado != null && montoConfigurado.signum() >= 0
                ? montoConfigurado : plan.getMaxAutoApprovalAmount();
    }

    private BigDecimal convertirMontoConfigurado(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            String limpio = valor.trim().replaceAll("[^0-9,.-]", "");
            if (limpio.isBlank()) return null;
            if (limpio.contains(",") && limpio.contains(".")) {
                limpio = limpio.replace(".", "").replace(",", ".");
            } else if (limpio.matches("-?\\d{1,3}(\\.\\d{3})+")) {
                limpio = limpio.replace(".", "");
            } else if (limpio.matches("-?\\d{1,3}(,\\d{3})+")) {
                limpio = limpio.replace(",", "");
            } else if (limpio.contains(",")) {
                limpio = limpio.replace(",", ".");
            }
            return new BigDecimal(limpio);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    // Calcula el ANS en días hábiles colombianos y marca una alerta si falla la API.
    private void calcularAnsProvisional(ClaimCase caso, String prioridad, boolean automatico) {
        if ("EN_LOTE_PAGO".equals(caso.getCurrentState()) || "OBJETADO".equals(caso.getCurrentState())) {
            caso.setAnsSemaphore("CUMPLIDO");
            caso.setAnsAlert(null);
            return;
        }
        if (caso.getFilingDate() == null) {
            caso.setAnsDeadlineDate(null);
            caso.setAnsSemaphore(null);
            caso.setAnsAlert("ANS sin validar festivos: falta fecha válida de radicación.");
            return;
        }

        int dias = automatico
                ? enteroParametro("ANS_AUTOMATICO_DIAS", 2)
                : switch (prioridad == null ? "MEDIA" : prioridad) {
                    case "ALTA" -> enteroParametro("ANS_ALTA_DIAS", 1);
                    case "BAJA" -> enteroParametro("ANS_BAJA_DIAS", 5);
                    default -> enteroParametro("ANS_MEDIA_DIAS", 3);
                };

        LocalDate fechaLimite = caso.getFilingDate();
        int diasHabilesContados = 0;
        Map<Integer, FestivosColombiaService.ResultadoFestivos> festivosPorAnio = new HashMap<>();
        boolean festivosValidados = true;

        while (diasHabilesContados < dias) {
            fechaLimite = fechaLimite.plusDays(1);
            DayOfWeek diaSemana = fechaLimite.getDayOfWeek();
            if (diaSemana == DayOfWeek.SATURDAY || diaSemana == DayOfWeek.SUNDAY) continue;

            int anio = fechaLimite.getYear();
            FestivosColombiaService.ResultadoFestivos calendario = festivosPorAnio.computeIfAbsent(
                    anio, festivosColombiaService::obtenerFestivos);
            if (!calendario.validados()) {
                festivosValidados = false;
                diasHabilesContados++;
                continue;
            }
            if (calendario.fechas().contains(fechaLimite)) continue;
            diasHabilesContados++;
        }

        caso.setAnsDeadlineDate(fechaLimite);
        caso.setAnsAlert(festivosValidados ? null : "ANS sin validar festivos");
        LocalDate referencia = fechaReferencia();
        if (referencia.isBefore(fechaLimite)) {
            caso.setAnsSemaphore("VERDE");
        } else if (referencia.isEqual(fechaLimite)) {
            caso.setAnsSemaphore("AMARILLO");
        } else {
            caso.setAnsSemaphore("ROJO");
        }
    }

    private LocalDate fechaReferencia() {
        String valor = textoParametro("FECHA_REFERENCIA", "");
        if (valor.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(valor);
        } catch (Exception ignorado) {
            return LocalDate.now();
        }
    }

    private int enteroParametro(String clave, int valorPorDefecto) {
        try {
            return Integer.parseInt(textoParametro(clave, String.valueOf(valorPorDefecto)).trim());
        } catch (Exception ignorado) {
            return valorPorDefecto;
        }
    }

    private BigDecimal decimalParametro(String clave, BigDecimal valorPorDefecto) {
        try {
            String valor = textoParametro(clave, valorPorDefecto.toPlainString()).trim();
            return new BigDecimal(valor);
        } catch (Exception ignorado) {
            return valorPorDefecto;
        }
    }

    private String textoParametro(String clave, String valorPorDefecto) {
        return parametros.findById(clave).map(ParameterConfig::getParameterValue).orElse(valorPorDefecto);
    }

    private Map<String, Object> crearFotoParametros() {
        Map<String, Object> foto = new LinkedHashMap<>();
        for (ParameterConfig parametro : parametros.findAll()) {
            foto.put(parametro.getParameterKey(), parametro.getParameterValue());
        }
        return foto;
    }

    private void registrarCambio(ClaimCase caso, String anterior, String nuevo, String actor, String comentario) {
        CaseTracking evento = new CaseTracking();
        evento.setClaimCaseId(caso.getId());
        evento.setPreviousState(anterior);
        evento.setNewState(nuevo);
        evento.setActorLabel(actor);
        evento.setCommentText(comentario);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("decisionMotor", true);
        evento.setEventMetadata(datos);
        tracking.save(evento);
    }

    private boolean esAfirmativo(Object valor) {
        if (valor == null) return false;
        String normalizado = normalizarTexto(valor.toString()).trim().toUpperCase(Locale.ROOT);
        return "SI".equals(normalizado) || "S".equals(normalizado);
    }

    private String nombreSoporte(String clave) {
        return switch (clave) {
            case "Factura_Adjunta" -> "factura";
            case "Historia_Clinica_Adjunta" -> "historia clínica";
            case "Orden_Medica_Adjunta" -> "orden médica";
            default -> clave;
        };
    }

    private String valorSeguro(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String normalizarCodigo(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarTexto(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private List<String> palabrasRiesgoConfiguradas() {
        String texto = textoParametro(
                "PALABRAS_R17",
                "[\"choque\", \"tránsito\", \"atropello\", \"moto\", \"andamio\", \"obra\", \"trabajo\", \"laboral\", \"agresión\", \"terceros\", \"rinoplastia\", \"estético\", \"liposucción\"]"
        );
        String limpio = texto.replace("[", "").replace("]", "").replace("\"", "");
        List<String> resultado = new ArrayList<>();
        for (String palabra : limpio.split(",")) {
            String normalizada = normalizarTexto(palabra).trim();
            if (!normalizada.isBlank()) resultado.add(normalizada);
        }
        return resultado;
    }

    private List<String> palabrasRiesgoDetectadas(String observaciones) {
        String texto = normalizarTexto(observaciones);
        List<String> detectadas = new ArrayList<>();
        for (String palabra : palabrasRiesgoConfiguradas()) {
            Pattern patron = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(palabra) + "(?![a-z0-9])");
            Matcher coincidencias = patron.matcher(texto);
            while (coincidencias.find()) {
                int inicio = Math.max(0, coincidencias.start() - 45);
                String contextoPrevio = texto.substring(inicio, coincidencias.start());
                boolean negada = Pattern.compile(
                        "(?:\\bniega(?:n)?\\b|\\bsin\\b|\\bdescarta(?:n)?\\b"
                                + "|\\bno\\s+(?:refiere|presenta|tuvo|reporta|hay)\\b)"
                                + "(?:[a-z0-9\\s]{0,30})$"
                ).matcher(contextoPrevio).find();
                if (!negada) {
                    detectadas.add(palabra);
                    break;
                }
            }
        }
        return detectadas.stream().distinct().toList();
    }

    private record ResultadoRegla(boolean cumple, String explicacion, Map<String, Object> evidencia) { }
}
