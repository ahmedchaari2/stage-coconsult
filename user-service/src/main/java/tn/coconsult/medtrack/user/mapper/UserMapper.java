package tn.coconsult.medtrack.user.mapper;

import org.mapstruct.Mapper;
import tn.coconsult.medtrack.user.dto.MedecinOptionResponse;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    MedecinOptionResponse toOptionResponse(User user);
}
