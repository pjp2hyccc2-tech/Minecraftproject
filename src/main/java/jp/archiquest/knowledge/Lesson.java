package jp.archiquest.knowledge;

import java.util.List;

/**
 * 教科書の1レッスン。
 *
 * @param summary   かみくだき解説（改行で段落区切り）
 * @param minecraft マイクラでの例え
 * @param points    試験に出るポイント
 * @param mnemonic  覚え方・ゴロ合わせ
 * @param keywords  検索用キーワード（オフライン先生が使う）
 */
public record Lesson(String id, Subject subject, String title, String summary, String minecraft,
                     List<String> points, String mnemonic, List<String> keywords) {
}
