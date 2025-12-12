    package com.company.company_clean_hub_be.entity;

    import java.math.BigDecimal;
    import java.time.DayOfWeek;
    import java.time.LocalDate;
    import java.time.LocalDateTime;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;

    import com.fasterxml.jackson.annotation.JsonIgnore;
    import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
    import jakarta.validation.constraints.NotEmpty;
    import jakarta.validation.constraints.NotNull;
    import jakarta.validation.constraints.PositiveOrZero;
    import jakarta.validation.constraints.Size;

    import jakarta.persistence.CollectionTable;
    import jakarta.persistence.Column;
    import jakarta.persistence.ElementCollection;
    import jakarta.persistence.Entity;
    import jakarta.persistence.EnumType;
    import jakarta.persistence.Enumerated;
    import jakarta.persistence.FetchType;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.JoinColumn;
    import jakarta.persistence.JoinTable;
    import jakarta.persistence.ManyToMany;
    import jakarta.persistence.ManyToOne;
    import jakarta.persistence.Table;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;
    import lombok.ToString;
    import lombok.EqualsAndHashCode;

    @Entity
    @Table(name = "contracts")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    public class Contract {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customer_id")
        @NotNull
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @JsonIgnore
        private Customer customer;

        @ManyToMany
        @JoinTable(
            name = "contract_services",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id")
        )
        @Builder.Default
        @NotEmpty
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @JsonIgnore
        private Set<ServiceEntity> services = new HashSet<>();

        @Column(name = "start_date")
        @NotNull
        private LocalDate startDate;

        @Column(name = "end_date")
        private LocalDate endDate;

        @ElementCollection(targetClass = DayOfWeek.class)
        @CollectionTable(name = "contract_working_days", joinColumns = @JoinColumn(name = "contract_id"))
        @Column(name = "day_of_week")
        @Enumerated(EnumType.STRING)
        private List<DayOfWeek> workingDaysPerWeek;

        @Column(name = "contract_type")
        @Enumerated(EnumType.STRING)
        private ContractType contractType;

        @Column(name = "final_price")
        @PositiveOrZero
        private BigDecimal finalPrice;

        @Column(name = "payment_status")
        @Size(max = 50)
        private String paymentStatus;

        private String description;

        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "updated_at")
        private LocalDateTime updatedAt;
    }
