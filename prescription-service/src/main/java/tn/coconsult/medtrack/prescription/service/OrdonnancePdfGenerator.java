package tn.coconsult.medtrack.prescription.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;
import tn.coconsult.medtrack.prescription.dto.PatientDetailsResponse;
import tn.coconsult.medtrack.prescription.model.Prescription;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Mise en page de l'ordonnance : en-tête médecin, bloc patient, prescriptions, signature. Reçoit des données déjà résolues, ne connaît ni repositories ni clients Feign. */
@Component
public class OrdonnancePdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font NOM_MEDECIN = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font TEXTE = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font MEDICAMENT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font SECONDAIRE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.DARK_GRAY);

    public byte[] generate(MedecinSummaryResponse medecin, PatientDetailsResponse patient,
                           List<Prescription> prescriptions, LocalDate dateEdition) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            addEnteteMedecin(document, medecin);
            addTitre(document, dateEdition);
            addBlocPatient(document, patient);
            addPrescriptions(document, prescriptions);
            addSignature(document);
            document.close();
        } catch (DocumentException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de générer l'ordonnance");
        }
        return out.toByteArray();
    }

    private void addEnteteMedecin(Document document, MedecinSummaryResponse medecin) {
        document.add(new Paragraph("Dr " + medecin.nom() + " " + medecin.prenom(), NOM_MEDECIN));
        if (StringUtils.hasText(medecin.specialite())) {
            document.add(new Paragraph(medecin.specialite(), SECONDAIRE));
        }
        if (StringUtils.hasText(medecin.numeroOrdre())) {
            document.add(new Paragraph("N° d'ordre : " + medecin.numeroOrdre(), SECONDAIRE));
        }
        document.add(separateur());
    }

    private void addTitre(Document document, LocalDate dateEdition) {
        Paragraph titre = new Paragraph("ORDONNANCE", TITRE);
        titre.setAlignment(Element.ALIGN_CENTER);
        titre.setSpacingBefore(12f);
        document.add(titre);

        Paragraph date = new Paragraph("Le " + dateEdition.format(DATE), TEXTE);
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setSpacingAfter(12f);
        document.add(date);
    }

    private void addBlocPatient(Document document, PatientDetailsResponse patient) {
        PdfPTable bloc = new PdfPTable(2);
        bloc.setWidthPercentage(100);
        bloc.setWidths(new float[]{1f, 3f});
        bloc.setSpacingAfter(18f);

        addLignePatient(bloc, "Patient", patient.nom() + " " + patient.prenom());
        if (patient.dateNaissance() != null) {
            addLignePatient(bloc, "Né(e) le", patient.dateNaissance().format(DATE));
        }
        addLignePatient(bloc, "N° de dossier", StringUtils.hasText(patient.numeroDossier()) ? patient.numeroDossier() : "-");

        document.add(bloc);
    }

    private void addLignePatient(PdfPTable bloc, String label, String valeur) {
        bloc.addCell(cellule(new Phrase(label, LABEL)));
        bloc.addCell(cellule(new Phrase(valeur, TEXTE)));
    }

    private PdfPCell cellule(Phrase contenu) {
        PdfPCell cell = new PdfPCell(contenu);
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        cell.setPaddingBottom(4f);
        return cell;
    }

    private void addPrescriptions(Document document, List<Prescription> prescriptions) {
        if (prescriptions.isEmpty()) {
            document.add(new Paragraph("Aucune prescription pour cette consultation.", SECONDAIRE));
            return;
        }

        int numero = 1;
        for (Prescription prescription : prescriptions) {
            Paragraph ligne = new Paragraph(numero++ + ". " + prescription.getMedicament(), MEDICAMENT);
            ligne.setSpacingBefore(10f);
            document.add(ligne);

            document.add(detail("Posologie : " + prescription.getPosologie()));
            if (prescription.getDureeJours() != null) {
                document.add(detail("Durée : " + prescription.getDureeJours() + " jour(s)"));
            }
            if (StringUtils.hasText(prescription.getInstructions())) {
                document.add(detail("Instructions : " + prescription.getInstructions()));
            }
            if (prescription.isRenouvelable()) {
                document.add(detail("Renouvelable"));
            }
        }
    }

    private Paragraph detail(String texte) {
        Paragraph paragraph = new Paragraph(texte, TEXTE);
        paragraph.setIndentationLeft(20f);
        return paragraph;
    }

    private void addSignature(Document document) {
        Paragraph signature = new Paragraph("Signature et cachet du médecin", SECONDAIRE);
        signature.setAlignment(Element.ALIGN_RIGHT);
        signature.setSpacingBefore(50f);
        document.add(signature);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
    }

    private Paragraph separateur() {
        Paragraph ligne = new Paragraph(new Chunk(new LineSeparator()));
        ligne.setSpacingBefore(6f);
        return ligne;
    }
}
