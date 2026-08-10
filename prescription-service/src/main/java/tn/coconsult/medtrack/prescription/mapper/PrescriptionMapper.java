package tn.coconsult.medtrack.prescription.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.coconsult.medtrack.prescription.dto.PrescriptionResponse;
import tn.coconsult.medtrack.prescription.model.Prescription;

@Mapper(componentModel = "spring")
public interface PrescriptionMapper {

    @Mapping(target = "consultationDate", ignore = true)
    @Mapping(target = "patientId", ignore = true)
    @Mapping(target = "patientNom", ignore = true)
    @Mapping(target = "patientPrenom", ignore = true)
    @Mapping(target = "medecinId", ignore = true)
    @Mapping(target = "medecinNom", ignore = true)
    @Mapping(target = "medecinPrenom", ignore = true)
    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "updatedByName", ignore = true)
    PrescriptionResponse toResponse(Prescription prescription);
}
