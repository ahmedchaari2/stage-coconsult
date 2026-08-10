package tn.coconsult.medtrack.medicalrecord.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.coconsult.medtrack.medicalrecord.dto.MedicalRecordResponse;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;

@Mapper(componentModel = "spring")
public interface MedicalRecordMapper {

    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "updatedByName", ignore = true)
    MedicalRecordResponse toResponse(MedicalRecord medicalRecord);
}
