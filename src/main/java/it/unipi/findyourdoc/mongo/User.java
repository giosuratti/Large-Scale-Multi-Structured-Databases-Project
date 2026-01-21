package it.unipi.findyourdoc.mongo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {
    @Id private int id;
    private String email;

    private String telephone;

    @Field("password_hash")
    private String password;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}

