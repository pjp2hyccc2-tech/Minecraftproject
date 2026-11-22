package jp.archiquest.knowledge;

/** 用語集の1語 */
public record GlossaryTerm(String term, String reading, Subject subject, String desc, String lesson) {
}
