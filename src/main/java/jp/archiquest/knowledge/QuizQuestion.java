package jp.archiquest.knowledge;

import java.util.List;

/**
 * クイズ1問（本番と同じ四肢択一が基本）。
 *
 * @param difficulty 1〜3（★の数）
 * @param answer     正解の choices インデックス（0始まり）
 * @param lesson     関連レッスンID（空でもよい）
 */
public record QuizQuestion(String id, Subject subject, int difficulty, String question,
                           List<String> choices, int answer, String explanation, String lesson) {
}
