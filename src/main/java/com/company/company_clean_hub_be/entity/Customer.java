package com.company.company_clean_hub_be.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customers")
@PrimaryKeyJoinColumn(name = "id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Customer extends User {

    @Column(name = "customer_code")
    @NotBlank
    @Size(max = 50)
    private String customerCode;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 255)
    private String address;

    @Column(name = "contact_info")
    @Size(max = 255)
    private String contactInfo;

    @Column(name = "tax_code")
    @Size(max = 100)
    private String taxCode;

    @Size(max = 1000)
    private String description;

    @Size(max = 255)
    private String company;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
