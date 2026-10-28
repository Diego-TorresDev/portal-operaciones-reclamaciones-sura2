package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

/** Historial de cambios de estado: cada evento se agrega, no se sobrescribe. */
@Entity
@Table(name = "case_tracking")
public class CaseTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "claim_case_id", nullable = false)
    private Long claimCaseId;
    @Column(name = "previous_state", length = 40)
    private String previousState;
    @Column(name = "new_state", length = 40, nullable = false)
    private String newState;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "actor_label", length = 100, nullable = false)
    private String actorLabel = "SISTEMA";
    @Column(name = "comment_text", columnDefinition = "text")
    private String commentText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> eventMetadata = new HashMap<>();
    @Column(name = "event_at", nullable = false)
    private Instant eventAt;
    public CaseTracking() { }
    @PrePersist protected void beforeInsert() {
        if (eventAt == null) eventAt = Instant.now();
        if (actorLabel == null || actorLabel.isBlank()) actorLabel = "SISTEMA";
        if (eventMetadata == null) eventMetadata = new HashMap<>();
    }
    public Long getId() { return id; }
    public Long getClaimCaseId() { return claimCaseId; }
    public void setClaimCaseId(Long v) { this.claimCaseId = v; }
    public String getPreviousState() { return previousState; }
    public void setPreviousState(String v) { this.previousState = v; }
    public String getNewState() { return newState; }
    public void setNewState(String v) { this.newState = v; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { this.actorUserId = v; }
    public String getActorLabel() { return actorLabel; }
    public void setActorLabel(String v) { this.actorLabel = v; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String v) { this.commentText = v; }
    public Map<String,Object> getEventMetadata() { return eventMetadata; }
    public void setEventMetadata(Map<String,Object> v) { this.eventMetadata = v; }
    public Instant getEventAt() { return eventAt; }
}
