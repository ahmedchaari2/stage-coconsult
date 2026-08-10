package tn.coconsult.medtrack.patient.mapper;

import org.mapstruct.Mapper;
import tn.coconsult.medtrack.patient.dto.PatientResponse;
import tn.coconsult.medtrack.patient.model.Patient;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    PatientResponse toResponse(Patient patient);
}
