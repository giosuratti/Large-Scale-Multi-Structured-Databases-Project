package it.unipi.findyourdoc.model.mongo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {
    @Id private String id;
    private String email;

    private String telephone;

    private String password;

    @CreatedDate

    private LocalDateTime createdAt;
}

