/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *
 *******************************************************************************/
package ch.elexis.data;

import org.apache.commons.lang3.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.interfaces.IOutputter;
import ch.elexis.core.model.BriefConstants;
import ch.elexis.core.model.IDocument;
import ch.elexis.core.model.IDocumentLetter;
import ch.elexis.core.model.IDocumentTemplate;
import ch.elexis.core.services.holder.CoreModelServiceHolder;
import ch.elexis.core.text.XRefExtensionConstants;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

/**
 * Ein Brief ist ein mit einem externen Programm erstelles Dokument. (Im Moment
 * immer OpenOffice.org). Die Klasse Briefe mit der Tabelle Briefe enthält dabei
 * die Meta-Informationen, während die private Klasse contents mit der Tabelle
 * HEAP die eigentlichen Dokumente als black box, nämlich im Binärformat des
 * erstellenden Programms, enthält. Ein Brief bezieht sich immer auf eine
 * bestimmte Konsultation, zu der er erstellt wurde.
 *
 * @author Gerry
 *
 */
public class Brief extends PersistentObject {
	public static final String FLD_MIME_TYPE = "MimeType";
	public static final String FLD_DATE_MODIFIED = "modifiziert";
	public static final String FLD_DATE = "Datum";
	public static final String FLD_TYPE = "Typ";
	public static final String FLD_KONSULTATION_ID = "BehandlungsID";
	public static final String FLD_DESTINATION_ID = "DestID";
	public static final String FLD_SENDER_ID = "AbsenderID";
	public static final String FLD_PATIENT_ID = "PatientID";
	public static final String FLD_SUBJECT = "Betreff";
	public static final String FLD_NOTE = "note";
	public static final String FLD_GELOESCHT = "geloescht";
	public static final String TABLENAME = "BRIEFE";
	public static final String TEMPLATE = BriefConstants.TEMPLATE;
	public static final String AUZ = BriefConstants.AUZ;
	public static final String RP = BriefConstants.RP;
	public static final String UNKNOWN = BriefConstants.UNKNOWN;
	public static final String LABOR = BriefConstants.LABOR;
	public static final String BESTELLUNG = BriefConstants.BESTELLUNG;
	public static final String RECHNUNG = BriefConstants.RECHNUNG;

	public static final String MIMETYPE_OO2 = "application/vnd.oasis.opendocument.text";
	public static final String SYS_TEMPLATE = "SYS";

	private Sticker dontAskForAddresseeSticker;

	@Override
	protected String getTableName() {
		return TABLENAME;
	}

	static {
		addMapping(TABLENAME, FLD_SUBJECT, FLD_PATIENT_ID, DATE_COMPOUND, FLD_SENDER_ID, FLD_DESTINATION_ID,
				FLD_KONSULTATION_ID, FLD_TYPE, "modifiziert=S:D:modifiziert", FLD_GELOESCHT, FLD_MIME_TYPE,
				"gedruckt=S:D:gedruckt", "Path", FLD_NOTE);
	}

	protected Brief() {/* leer */
	}

	protected Brief(String id) {
		super(id);
	}

	/** Einen Brief anhand der ID aus der Datenbank laden */
	public static Brief load(String id) {
		return new Brief(id);
	}

	/**
	 * Convenience conversion method, loads object via model service
	 *
	 * @return
	 * @since 3.8
	 * @throws IllegalStateException if entity could not be loaded
	 */
	public IDocument toIDocument() {
		if (BriefConstants.TEMPLATE.equals(getTyp())) {
			return CoreModelServiceHolder.get().load(getId(), IDocumentTemplate.class)
					.orElseThrow(() -> new IllegalStateException("Could not convert Brief [" + getId() + "]"));
		} else {
			return CoreModelServiceHolder.get().load(getId(), IDocumentLetter.class)
					.orElseThrow(() -> new IllegalStateException("Could not convert Brief [" + getId() + "]"));
		}
	}

	/** Einen neuen Briefeintrag erstellen */
	public Brief(String Betreff, TimeTool Datum, Kontakt Absender, Kontakt dest, Konsultation bh, String typ) {
		try {
			super.create(null);
			if (Datum == null) {
				Datum = new TimeTool();
			}
			String pat = StringTool.leer, bhdl = StringTool.leer;
			if (bh != null) {
				bhdl = bh.getId();
				pat = bh.getFall().getPatient().getId();
			}
			String dst = StringUtils.EMPTY;
			if (dest != null) {
				dst = dest.getId();
			}
			String dat = Datum.toString(TimeTool.TIMESTAMP);
			set(new String[] { FLD_SUBJECT, FLD_PATIENT_ID, FLD_DATE, FLD_SENDER_ID, FLD_DATE_MODIFIED,
					FLD_DESTINATION_ID, FLD_KONSULTATION_ID, FLD_TYPE, FLD_GELOESCHT },
					new String[] { Betreff, pat, dat, Absender == null ? StringTool.leer : Absender.getId(), dat, dst,
							bhdl, typ, StringConstants.ZERO });
		} catch (Throwable ex) {
			ExHandler.handle(ex);
		}
	}

	public void setPatient(Person k) {
		set(FLD_PATIENT_ID, k.getId());
	}

	public void setTyp(String typ) {
		set(FLD_TYPE, typ);
	}

	public String getTyp() {
		String t = get(FLD_TYPE);
		if (t == null) {
			return "Brief";
		}
		return t;
	}

	/** Speichern als Text */
	public boolean save(String cnt) {
        System.out.println("Brief.java: Speichern als Text save() begins...");
		return save(cnt.getBytes(), "txt");
	}

	/** Speichern in Binärformat */
	public boolean save(byte[] in, String mimetype) {
        System.out.println("Brief.java: Speichern in Binaerformat save() begins...");
        /*
         * //202601021601js: Es braucht ZWINGEND diesen Test, ob (in != null) ist.
         * 
         * In der frueheren (einfacheren) Implementation mindestens bis 3.7 war der drin.
         * Upstream hat den aber irgendwann entfernt, vielleicht in der Annahme, dass das nicht vorkommen wuerde,
         * oder dass ein try... catch... das dann schon richten werde.
         * 
         * Der Ablauf hier versucht jedoch offenbar vor jedem Oeffnen eines Dokuments, ein potentiell noch vorhandenes frueheres zu speichern.
         * Ab dem zweiten zu oeffnenden Brief ist in meinem Setup dann (in == null); aber save() wird trotzdem aufgerufen,
         * und der test if (in != null) ... hat das seinerzeit einfach ohne Nebenwirkungen wieder zurueckkehren lassen.
         * 
         * Mit der neuen Implementation (vermutlich ab etwa 3.10..3.11..3.13 ??? fehlte jedoch diese Pruefung.
         * In der Folge wirft das Oeffnen (!) des naechsten Briefes mindestens 3-mal einen Fehlerdialog -
         * statt, wie es sein sollte, zwar hier kurz vorbeizuschauen - aber dann einfach still und leise umzukehren,
         * weil es nichts zu speichern gibt, und sofort darauf den gewuenschten Brief zu oeffnen.
         * 
         * Nachdem ich die Pruefung hier wieder eingefuegt habe, funktioniert das alles wieder reibungslos.
         * 
         * Im Zuge des Debuggings dahin habe ich die System.out.println() Meldungen vor den verschiedenen Methoden von Brief.java ergaenzt.
         * Wenn sich das alles (WIEDER! sic!) als langfristig stabil erweist, koennen diese auch irgendwann wieder entfernt werden.
         */
        if (in != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(in)) {
                IDocument iDocument = toIDocument();
                iDocument.setMimeType(mimetype);
                iDocument.setContent(inputStream);
                CoreModelServiceHolder.get().save(iDocument);
                return true;
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).warn("Error saving content of Brief [{}]", getId(), e);
            }
        }
        return false;
	}

	/** Binärformat laden */
	public byte[] loadBinary() {
        System.out.println("Brief.java: Binaerformat laden loadBinary() begins...");
		byte[] content = null;
		try (InputStream is = toIDocument().getContent()) {
			content = IOUtils.toByteArray(is);
		} catch (IOException e) {
			LoggerFactory.getLogger(getClass()).warn("Error loading content of Brief [{}]", getId(), e);
		}
		return content;
	}

	/** Textformat laden */
	public String read() {
        System.out.println("Brief.java: Textformat laden read() begins...");
		return new String(loadBinary());
	}

	/** Mime-Typ des Inhalts holen */
	public String getMimeType() {
        System.out.println("Brief.java: getMimeType() begins...");
		String gm = get(FLD_MIME_TYPE);
		if (StringTool.isNothing(gm)) {
			return MIMETYPE_OO2;
		}
		return gm;
	}

	public static boolean canHandle(String mimetype) {
		/*
		 * if(mimetype.equalsIgnoreCase(MIMETYPE_OO2)){ return true; }
		 */
		return true;
	}

	@Override
	public boolean delete() {
		String konsID = get(FLD_KONSULTATION_ID);
		if (!StringTool.isNothing(konsID) && (!konsID.equals(SYS_TEMPLATE))) {
			Konsultation kons = Konsultation.load(konsID);
			if ((kons != null) && kons.exists() && (kons.isEditable(false))) {
				kons.removeXRef(XRefExtensionConstants.providerID, getId());
			}
		}
		CoreModelServiceHolder.get().delete(toIDocument());
		return super.delete();
	}

	public OutputLog logOutput(IOutputter outputter) {
		return new OutputLog(this, outputter);
	}

	/**
	 * Remove the content. Can not be reverted.
	 *
	 * @return
	 */
	public void removeContent() {
		IDocument iDocument = toIDocument();
		iDocument.setContent(null);
		CoreModelServiceHolder.get().save(iDocument);
	}

	public String getBetreff() {
		return checkNull(get(FLD_SUBJECT));
	}

	public void setBetreff(String nBetreff) {
		set(FLD_SUBJECT, nBetreff);
	}

	public String getDatum() {
		return new TimeTool(get(FLD_DATE)).toString(TimeTool.DATE_GER);
	}

	public Kontakt getAdressat() {
		String dest = get(FLD_DESTINATION_ID);
		return dest == null ? null : Kontakt.load(dest);
	}

	public void setAdressat(String adressatId) {
		set(FLD_DESTINATION_ID, adressatId);
	}

	public Person getPatient() {
		Person pat = Person.load(get(FLD_PATIENT_ID));
		if ((pat != null) && (pat.state() > INVALID_ID)) {
			return pat;
		}
		return null;
	}

	@Override
	public String getLabel() {
		return checkNull(get(FLD_DATE)) + StringTool.space + checkNull(get(FLD_SUBJECT));
	}

	public boolean isAskForAddressee() {
		return !getStickers().stream()
				.filter(s -> ((Sticker) s).get(Sticker.FLD_NAME).equals(BriefConstants.DONT_ASK_FOR_ADDRESS_STICKER))
				.findFirst().isPresent();
	}
}
