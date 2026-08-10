package tn.coconsult.medtrack.medicalrecord.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.coconsult.medtrack.medicalrecord.dto.ConsultationResponse;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;

@Mapper(componentModel = "spring")
public interface ConsultationMapper {

    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "updatedByName", ignore = true)
    ConsultationResponse toResponse(Consultation consultation);
}
