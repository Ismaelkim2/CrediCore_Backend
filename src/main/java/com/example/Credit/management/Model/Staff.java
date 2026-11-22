
package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    private String phone;

    private String department;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            unique = true
    )
    private User user;
}
