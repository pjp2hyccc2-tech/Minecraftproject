package jp.archiquest.analysis;

/**
 * 診断で見つかった部屋（壁・床・天井で囲まれた空間を、床の高さごとに分けたもの）。
 * 1ブロック = 1m として計算する。
 *
 * @param levelY     床の高さ（Y座標）
 * @param story      階（1=1F, 2=2F, -1=B1F）
 * @param floorArea  床面積（m²）
 * @param volume     空間の体積（m³）
 * @param windows    壁のガラス（m²）
 * @param skylights  天井のガラス＝天窓（m²）
 * @param openings   ドア・トラップドア・フェンスゲート（m²）
 * @param lights     照明（光るブロック）の数
 * @param linkedExit この階にドアはないが、階段や吹き抜けでつながった別の階から出られる
 */
public record RoomInfo(int levelY, int story, int floorArea, int volume, double avgCeiling,
                       int windows, int skylights, int openings, int lights, boolean bed, boolean linkedExit,
                       double cx, double cy, double cz) {

    public RoomInfo withStory(int s) {
        return new RoomInfo(levelY, s, floorArea, volume, avgCeiling, windows, skylights, openings, lights, bed, linkedExit, cx, cy, cz);
    }

    /** 採光に有効な面積の比率。天窓は3倍で計算（建築基準法施行令20条の考え方を簡略化） */
    public double lightRatio() {
        return floorArea == 0 ? 0 : (windows + 3.0 * skylights) / floorArea;
    }

    public double ventRatio() {
        return floorArea == 0 ? 0 : (double) openings / floorArea;
    }

    /** 4m²以上を「居室」として判定する（それ未満は納戸・トイレ扱い） */
    public boolean habitable() {
        return floorArea >= 4;
    }

    public boolean ceilingOk() {
        return avgCeiling >= 2.1 - 1e-9;
    }

    public boolean lightOk() {
        return lightRatio() >= 1.0 / 7 - 1e-9;
    }

    /** 照明設備（床面50lx以上）を設ければ1/10まで緩和（令和5年4月施行） */
    public boolean lightRelaxedOk() {
        return lights > 0 && lightRatio() >= 1.0 / 10 - 1e-9;
    }

    public boolean lightAcceptable() {
        return lightOk() || lightRelaxedOk();
    }

    public boolean ventOk() {
        return ventRatio() >= 1.0 / 20 - 1e-9;
    }

    public boolean hasExit() {
        return openings > 0 || linkedExit;
    }

    public boolean allOk() {
        return ceilingOk() && lightAcceptable() && ventOk() && hasExit();
    }

    public String storyLabel() {
        return story > 0 ? story + "F" : "B" + (-story) + "F";
    }
}
