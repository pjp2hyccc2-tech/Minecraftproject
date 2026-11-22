package jp.archiquest.knowledge;

import java.util.List;

/** 建築中アドバイスのカテゴリ（ブロックの種類ごと） */
public record TipCategory(String id, String label, Subject subject, String lesson, List<String> tips) {
}
