package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {
    @Id
    private String id;
    @Indexed(unique = false)
    private String email;

    private String telephone;

    private String password;

    @CreatedDate

    private LocalDateTime createdAt;
}

