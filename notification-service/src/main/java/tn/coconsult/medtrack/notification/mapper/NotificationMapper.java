package tn.coconsult.medtrack.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.coconsult.medtrack.notification.dto.NotificationResponse;
import tn.coconsult.medtrack.notification.model.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "data", ignore = true)
    NotificationResponse toResponse(Notification notification);
}
