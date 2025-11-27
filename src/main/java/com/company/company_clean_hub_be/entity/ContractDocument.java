package com.company.company_clean_hub_be.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "contract_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ContractDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Contract contract;
    
    @Column(name = "cloudinary_public_id")
    @NotBlank
    @Size(max = 512)
    private String cloudinaryPublicId;

    @Column(name = "document_type")
    @Enumerated(EnumType.STRING)
    @NotNull
    private DocumentType documentType;

    @Column(name = "file_name")
    @NotBlank
    @Size(max = 255)
    private String fileName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    public enum DocumentType {
        IMAGE("image"),
        PDF("pdf");

        private final String type;

        DocumentType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public static DocumentType fromString(String type) {
            for (DocumentType dt : DocumentType.values()) {
                if (dt.type.equalsIgnoreCase(type)) {
                    return dt;
                }
            }
            throw new IllegalArgumentException("Unknown document type: " + type);
        }
    }
}
