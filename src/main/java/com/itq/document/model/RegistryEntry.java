package com.itq.document.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "approval_registry")
@Getter
@Setter
@NoArgsConstructor
public class RegistryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    private Document document;

    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "comment")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Защита ID
    public void setId(Long id) {
        if (this.id != null) {
            throw new UnsupportedOperationException("ID is already set");
        }
        this.id = id;
    }

    // equals и hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryEntry that = (RegistryEntry) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RegistryEntry{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", approvedBy='" + approvedBy + '\'' +
                ", approvedAt=" + approvedAt +
                ", comment='" + comment + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}