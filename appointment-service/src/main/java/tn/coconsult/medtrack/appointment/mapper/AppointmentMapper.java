package tn.coconsult.medtrack.appointment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.coconsult.medtrack.appointment.dto.AppointmentResponse;
import tn.coconsult.medtrack.appointment.model.Appointment;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "patientNom", ignore = true)
    @Mapping(target = "patientPrenom", ignore = true)
    @Mapping(target = "medecinNom", ignore = true)
    @Mapping(target = "medecinPrenom", ignore = true)
    AppointmentResponse toResponse(Appointment appointment);
}
