"""Language tables (Japanese first, English second) for MINECRAFT BESIEGE."""
import json
import os

PARTS = {
    'starting_block': ('始動ブロック', 'Starting Block', 'マシンの心臓部。すべてのパーツはここにつながる。上面の矢印が前方', 'The heart of every machine. The arrow on top marks forward'),
    'wooden_block': ('木材ブロック', 'Wooden Block', '軽くて安い基本の骨組み。燃えやすい', 'Light, cheap frame block. Burns easily'),
    'reinforced_wood': ('補強木材', 'Reinforced Wood', '鉄の帯で補強した丈夫な木材', 'Wood bound with iron bands; much tougher'),
    'wooden_pole': ('木の柱', 'Wooden Pole', '細い木の柱。軽量な骨組みに', 'Thin pole for light frames'),
    'wooden_panel': ('木の板', 'Wooden Panel', '薄い板。矢を防ぐ', 'Thin boards; stops arrows'),
    'seat': ('座席', 'Seat', '近くに立ってシミュレーション開始で乗車。WASDで操縦', 'Stand next to it when starting to ride along; WASD drives'),
    'iron_block': ('鉄ブロック', 'Iron Block', '重くて頑丈。燃えない', 'Heavy, tough and fireproof'),
    'steel_block': ('鋼ブロック', 'Steel Block', '最も頑丈な装甲ブロック', 'The toughest armour block'),
    'iron_plate': ('鉄板', 'Iron Plate', '薄い装甲板。背面で取り付け', 'Thin armour plate, attaches by its back'),
    'stone_block': ('石ブロック', 'Stone Block', '重く燃えない。水に沈みにくい', 'Heavy and fireproof'),
    'wheel': ('木の車輪', 'Wooden Wheel', '動力付き車輪。↑↓で前進/後退', 'Powered wheel. Up/Down to drive'),
    'iron_wheel': ('鉄の車輪', 'Iron Wheel', '重く頑丈な動力車輪。トルクが大きい', 'Heavy, strong powered wheel with more torque'),
    'large_tire': ('大型タイヤ', 'Large Tyre', 'グリップ抜群の大きなゴムタイヤ', 'Big rubber tyre with great grip'),
    'small_wheel': ('小型キャスター', 'Small Caster', '動力なしの小さな車輪', 'Small unpowered wheel'),
    'grip_pad': ('グリップパッド', 'Grip Pad', '摩擦が非常に大きい', 'Very high friction'),
    'ice_pad': ('氷の滑り板', 'Ice Pad', 'ほとんど摩擦がない', 'Almost no friction'),
    'hinge': ('ヒンジ', 'Hinge', '自由に曲がる関節（ピン軸）。スニーク+レンチで軸を回転', 'Free joint around its pin; sneak+wrench turns the pin'),
    'steering_hinge': ('ステアリングヒンジ', 'Steering Hinge', '車体の側面に付けて車輪を載せると←→で首を振る（制限角あり）。スニーク+レンチで軸を回転', 'Mount on a side with a wheel on it: swivels with Left/Right within a limit; sneak+wrench turns the pin'),
    'steering_block': ('ステアリングブロック', 'Steering Block', '←→で無制限に回転し、その角度を保持', 'Rotates without limit and holds its angle'),
    'spinning_block': ('回転ブロック', 'Spinning Block', '回り続けるモーター。プロペラやノコギリに', 'Motor that keeps spinning; for propellers and blades'),
    'ball_joint': ('ボールジョイント', 'Ball Joint', 'あらゆる方向に曲がる関節', 'Bends in every direction'),
    'suspension': ('サスペンション', 'Suspension', 'バネとダンパーで衝撃を吸収', 'Spring and damper that soak up bumps'),
    'piston': ('ピストン', 'Piston', 'Hで伸び縮み（トグル）', 'Extends / retracts with H'),
    'decoupler': ('デカップラー', 'Decoupler', 'Jで切り離し、爆発力で押し離す', 'Splits apart with a small blast on J'),
    'grabber': ('グラバー', 'Grabber', '触れた物をつかむ。Vで放す', 'Grabs whatever it touches; V releases'),
    'cannon': ('大砲', 'Cannon', 'Cで鉄の砲弾を発射。反動に注意！', 'Fires an iron cannonball on C. Mind the recoil!'),
    'crossbow': ('弩', 'Crossbow', 'Cで矢を発射。弾数制限あり', 'Fires arrows on C; limited ammo'),
    'flamethrower': ('火炎放射器', 'Flamethrower', 'Yで炎を噴射。木やモブを燃やす', 'Breathes fire on Y; burns wood and mobs'),
    'bomb': ('爆薬（TNT）', 'TNT Bomb', '強い衝撃や炎で爆発する', 'Explodes on hard impact or fire'),
    'powder_barrel': ('火薬樽', 'Powder Barrel', '大爆発する火薬樽。火気厳禁', 'Huge blast; keep away from fire'),
    'boulder': ('投石用弾', 'Boulder', '重い岩。Xで放す（投石機に）', 'Heavy rock released with X (catapults)'),
    'rocket': ('ロケット', 'Rocket', 'Kで点火。数秒間の強い推力', 'Ignites on K for a few seconds of thrust'),
    'saw_blade': ('回転刃', 'Circular Saw', '高速回転して触れたものを切り裂く', 'Spins fast and shreds what it touches'),
    'drill': ('ドリル', 'Drill', '回転しながらブロックを掘り進む', 'Spins and bores through blocks'),
    'hammer': ('ハンマー', 'Hammer', '重い鉄槌。振り回して叩きつける', 'Heavy iron head for smashing'),
    'spike': ('スパイク', 'Spike', '触れた敵に大ダメージ', 'Deals heavy damage on contact'),
    'propeller': ('プロペラ', 'Propeller', '回転ブロックで回すと推力を生む', 'Makes thrust when spun by a spinning block'),
    'wing': ('ウィング', 'Wing', '空気を受けて揚力を生む', 'Makes lift from airflow'),
    'flying_block': ('飛行ブロック', 'Flying Block', 'Uで前方に推力（花火スラスター）', 'Firework thruster: pushes forward on U'),
    'balloon': ('風船', 'Balloon', 'マシンを持ち上げる。割れやすい', 'Lifts the machine; pops easily'),
    'ballast': ('バラスト', 'Ballast', '重さを調整できるおもり', 'Weight with adjustable mass'),
    'timer': ('タイマー', 'Timer', 'トリガー後、遅れて出力キーを押す', 'Presses its output key after a delay'),
    'sensor': ('センサー', 'Sensor', '前方に何かあると出力キーを押す', 'Presses its output key when something is ahead'),
    'altimeter': ('高度計', 'Altimeter', '設定高度で出力キーを押す', 'Presses its output key at a set height'),
    'logic_gate': ('論理ゲート', 'Logic Gate', 'AND/OR/XOR…で入力キーを組み合わせる', 'Combines input keys: AND / OR / XOR …'),
}

OTHER_BLOCKS = {
    'castle_stone': ('城壁の石', 'Castle Stone'), 'castle_bricks': ('城レンガ', 'Castle Bricks'),
    'castle_brick_stairs': ('城レンガの階段', 'Castle Brick Stairs'), 'castle_brick_slab': ('城レンガのハーフブロック', 'Castle Brick Slab'),
    'timber_frame': ('木骨壁', 'Timber Frame'), 'timber_frame_plain': ('木骨壁（縦）', 'Plain Timber Frame'), 'thatch': ('茅葺き', 'Thatch'),
    'thatch_stairs': ('茅葺きの階段', 'Thatch Stairs'), 'thatch_slab': ('茅葺きのハーフブロック', 'Thatch Slab'), 'castle_gate': ('城門', 'Castle Gate'),
    'banner_blue': ('青の軍旗', 'Blue War Banner'), 'banner_red': ('赤の軍旗', 'Red War Banner'), 'siege_target': ('攻城目標', 'Siege Target'),
    'mission_table': ('作戦テーブル', 'Mission Table'), 'brace_visual': ('ブレース', 'Brace'), 'spring_visual': ('スプリング', 'Spring'),
    'rope_visual': ('ロープ', 'Rope'),
}

ITEMS = {
    'wrench': ('レンチ（キーマッパー）', 'Wrench (Key Mapper)', 'パーツを右クリックでキー・設定の変更', 'Right-click a part to edit keys and settings'),
    'brace_tool': ('ブレース', 'Brace', '2つのパーツをつなぐ補強材', 'Rigid strut between two parts'),
    'spring_tool': ('スプリング', 'Contractible Spring', 'Lで縮むバネ（投石機に）', 'Spring that contracts on L (catapults)'),
    'rope_tool': ('ロープ＆ウインチ', 'Rope & Winch', 'O/Pで巻き上げ・繰り出し', 'Wind / unwind with O / P'),
    'blueprint': ('設計図', 'Blueprint', 'マシンを保存して複製', 'Save and copy machines'),
    'cannonball': ('鉄の砲弾', 'Cannonball', '', ''),
}

ACTIONS = {'forward': ('前進', 'Forward'), 'backward': ('後退', 'Backward'), 'left': ('左', 'Left'), 'right': ('右', 'Right'), 'spin': ('回転', 'Spin'),
           'extend': ('伸縮', 'Extend'), 'detach': ('切り離し', 'Detach'), 'release': ('放す', 'Release'), 'fire': ('発射', 'Fire'),
           'detonate': ('起爆', 'Detonate'), 'launch': ('点火', 'Launch'), 'thrust': ('推進', 'Thrust'), 'trigger': ('トリガー', 'Trigger'),
           'output': ('出力', 'Output'), 'input_a': ('入力A', 'Input A'), 'input_b': ('入力B', 'Input B'), 'contract': ('収縮', 'Contract'),
           'wind': ('巻き上げ', 'Wind'), 'unwind': ('繰り出し', 'Unwind')}

SLIDERS = {'speed': ('速度', 'Speed'), 'angle': ('角度', 'Angle'), 'limit': ('制限角', 'Limit'), 'spring': ('バネ', 'Spring'),
           'damper': ('減衰', 'Damper'), 'force': ('爆発力', 'Force'), 'power': ('威力', 'Power'), 'ammo': ('弾数', 'Ammo'),
           'fuel': ('燃料（秒）', 'Fuel (s)'), 'thrust': ('推力', 'Thrust'), 'duration': ('持続（秒）', 'Duration (s)'),
           'buoyancy': ('浮力', 'Buoyancy'), 'mass': ('質量', 'Mass'), 'delay': ('遅延（秒）', 'Delay (s)'), 'range': ('範囲', 'Range'),
           'height': ('高度', 'Height'), 'mode': ('モード', 'Mode')}

TOGGLES = {'automatic': ('自動', 'Automatic'), 'toggle': ('トグル動作', 'Toggle mode'), 'autobrake': ('自動ブレーキ', 'Auto brake'),
           'flip': ('逆回転', 'Flip direction'), 'powered': ('動力', 'Powered'), 'limits': ('角度制限', 'Angle limits'),
           'return': ('中央に戻る', 'Return to centre'), 'explode': ('終了時に爆発', 'Explode at end'), 'entities': ('エンティティ検知', 'Detect entities'),
           'blocks': ('ブロック検知', 'Detect blocks'), 'invert': ('反転', 'Invert'), 'above': ('上で作動', 'Fire when above')}

CATS = {'basic': ('基本', 'Basic'), 'armor': ('装甲', 'Armour'), 'locomotion': ('移動', 'Locomotion'), 'mechanical': ('機械', 'Mechanical'),
        'weapons': ('武器', 'Weapons'), 'flight': ('飛行', 'Flight'), 'automation': ('自動化', 'Automation')}

MISSIONS = {
    'first_siege': ('はじめての攻城', 'First Siege', 'かかしの上の攻城目標を壊そう。車輪で体当たりするか、大砲で撃ち抜け！',
                    'Smash the siege target on the scarecrow: ram it with wheels or shoot it with a cannon!'),
    'windmill': ('風車を壊せ', 'Destroy the Windmill', '村の風車にある2つの攻城目標を破壊せよ。', 'Destroy both siege targets of the village windmill.'),
    'outpost': ('前哨基地を掃討せよ', 'Clear the Outpost', '略奪者の見張り塔を攻め、敵兵をすべて倒せ。', 'Storm the pillager watchtower and defeat every enemy.'),
    'castle_gate': ('城門を破れ', 'Breach the Gate', '城門を突破し、中庭にある3つの攻城目標を破壊せよ。',
                    'Break through the castle gate and destroy the three targets in the courtyard.'),
    'treasure': ('宝を運べ', 'Deliver the Treasure', '重い宝物ブロックを金のゴールまで運べ。', 'Carry the heavy treasure block into the golden goal.'),
    'canyon': ('谷を越えろ', 'Cross the Canyon', '深い谷を越えて、向こう岸の金のゴールにマシンを到達させよ。',
               'Get your machine across the canyon into the golden goal.'),
    'three_towers': ('三つの塔', 'Three Towers', '三つの見張り塔の頂上にある攻城目標を破壊せよ。', 'Destroy the targets on top of the three watchtowers.'),
    'ravager': ('ラヴェジャー討伐', 'Slay the Ravager', '柵の中の巨獣ラヴェジャーと護衛を倒せ。', 'Defeat the ravager and its guards inside the palisade.'),
    'kings_castle': ('王の城', "The King's Castle", '城壁を越え、天守の4つの目標を破壊し、守備兵を全滅させよ。',
                     'Get over the walls, destroy the four targets on the keep and defeat every defender.'),
}

MISC = {
    'itemGroup.mcbesiege.main': ('Minecraft Besiege - 攻城兵器', 'Minecraft Besiege - Siege Machines'),
    'itemGroup.mcbesiege.fortress': ('Minecraft Besiege - 城と要塞', 'Minecraft Besiege - Castles'),
    'key.categories.mcbesiege': ('Minecraft Besiege', 'Minecraft Besiege'),
    'key.mcbesiege.simulate': ('シミュレーション開始 / リセット', 'Simulate / reset'),
    'key.mcbesiege.control': ('操縦モード', 'Control mode'),
    'key.mcbesiege.builder': ('パーツパレット', 'Part palette'),
    'key.mcbesiege.mirror': ('ミラー建築 切替', 'Toggle mirror building'),
    'msg.mcbesiege.err.no_start': ('始動ブロックが見つかりません', 'No starting block found'),
    'msg.mcbesiege.err.too_big': ('マシンが大きすぎます', 'The machine is too big'),
    'msg.mcbesiege.err.two_starts': ('始動ブロックは1つのマシンに1つだけです', 'A machine can only have one starting block'),
    'msg.mcbesiege.started': ('⚔ シミュレーション開始！（%s パーツ）　[R]でリセット', '⚔ Simulation started! (%s parts)  [R] resets'),
    'msg.mcbesiege.reset': ('マシンをリセットしました', 'Machine reset'),
    'msg.mcbesiege.no_start': ('近くに始動ブロックがありません', 'No starting block nearby'),
    'msg.mcbesiege.not_upright': ('マシンが傾きすぎていて設置できません', 'The machine is tilted too much to place'),
    'msg.mcbesiege.placed': ('マシンをその場に設置しました', 'Machine placed where it stands'),
    'msg.mcbesiege.mirror_on': ('ミラー建築: ON', 'Mirror building: ON'),
    'msg.mcbesiege.mirror_off': ('ミラー建築: OFF', 'Mirror building: OFF'),
    'msg.mcbesiege.need_creative': ('パレットはクリエイティブかミッション中のみ使えます', 'The palette works in creative or during missions'),
    'msg.mcbesiege.cleared': ('マシンを消去しました', 'Machine cleared'),
    'msg.mcbesiege.connector_first': ('始点を選びました。つなぐ先のパーツの面をクリック', 'Start chosen. Now click a face of the other part'),
    'msg.mcbesiege.connector_far': ('遠すぎます（16ブロック以内）', 'Too far (max 16 blocks)'),
    'msg.mcbesiege.connector_done': ('%s を取り付けました', '%s attached'),
    'msg.mcbesiege.connector_cancel': ('選択を解除しました', 'Selection cleared'),
    'msg.mcbesiege.bp_saved': ('設計図に保存しました（%s パーツ）', 'Saved to blueprint (%s parts)'),
    'msg.mcbesiege.bp_missing': ('材料が足りません: %s × %s', 'Missing materials: %s × %s'),
    'msg.mcbesiege.bp_blocked': ('設置場所がふさがっています', 'Something is in the way'),
    'msg.mcbesiege.bp_built': ('設計図からマシンを建造しました（%s パーツ）', 'Built the machine from the blueprint (%s parts)'),
    'msg.mcbesiege.target_hand': ('攻城目標は兵器で破壊しよう！', 'Destroy siege targets with your machine!'),
    'msg.mcbesiege.no_mission': ('ミッションが見つかりません', 'Unknown mission'),
    'msg.mcbesiege.generating': ('戦場を生成中…', 'Generating the battlefield…'),
    'msg.mcbesiege.arena_generating': ('戦場の生成中です。少し待ってください', 'The battlefield is still being generated'),
    'msg.mcbesiege.outside_zone': ('ビルドゾーンの外にパーツがあります！', 'Some parts are outside the build zone!'),
    'msg.mcbesiege.build_hint': ('黄色いビルドゾーンの中でマシンを作り、[R]で出撃！ [B]でパーツ一覧',
                                 'Build inside the yellow build zone and press [R] to attack! [B] opens the parts'),
    'msg.mcbesiege.victory_hint': ('作戦テーブルか /mcbesiege leave で帰還できます', 'Use a mission table or /mcbesiege leave to go home'),
    'button.mcbesiege.reset': ('リセット', 'Reset'),
    'button.mcbesiege.place_here': ('その場に設置', 'Place here'),
    'button.mcbesiege.exit': ('操作終了', 'Exit'),
    'button.mcbesiege.remove': ('削除', 'Remove'),
    'button.mcbesiege.apply_all': ('同種すべてに適用', 'Apply to all of type'),
    'button.mcbesiege.mission_start': ('出撃！', 'Deploy!'),
    'button.mcbesiege.mission_leave': ('撤退して帰還', 'Retreat home'),
    'screen.mcbesiege.control': ('操縦', 'Control'),
    'screen.mcbesiege.control_title': ('⚔ 操縦モード', '⚔ Control mode'),
    'screen.mcbesiege.no_keys': ('キー割り当てなし', 'No key bindings'),
    'screen.mcbesiege.integrity': ('耐久: %s / %s パーツ', 'Integrity: %s / %s parts'),
    'screen.mcbesiege.control_hint': ('[Esc] 操作終了　[%s] リセット　ドラッグ: カメラ回転　ホイール: ズーム',
                                      '[Esc] exit  [%s] reset  drag: orbit camera  wheel: zoom'),
    'screen.mcbesiege.part_config': ('パーツ設定', 'Part settings'),
    'screen.mcbesiege.no_settings': ('このパーツに設定項目はありません', 'This part has no settings'),
    'screen.mcbesiege.press_key': ('キーを押してください（Esc: キャンセル / BS: 解除）', 'Press a key (Esc: cancel / Backspace: clear)'),
    'screen.mcbesiege.builder': ('パーツパレット', 'Part palette'),
    'screen.mcbesiege.builder_hint': ('クリックでパーツを入手（クリエイティブ／ミッション中）', 'Click a part to take it (creative / missions)'),
    'screen.mcbesiege.missions': ('作戦テーブル — 攻城作戦', 'Mission Table — Siege Campaign'),
    'tool.mcbesiege.play': ('▶ シミュレーション開始 / リセット', '▶ Simulate / reset'),
    'tool.mcbesiege.settings': ('⚙ 見ているパーツの設定', '⚙ Settings of the part you look at'),
    'tool.mcbesiege.trash': ('🗑 見ているマシンを消去（始動ブロック以外）', '🗑 Clear the machine you look at (keeps the starting block)'),
    'tool.mcbesiege.trash_confirm': ('もう一度クリックで消去', 'Click again to clear'),
    'tool.mcbesiege.mirror': ('⇆ ミラー建築 切替', '⇆ Toggle mirror building'),
    'island.mcbesiege.1': ('第一の島：辺境の村', 'Island I: Frontier Village'),
    'island.mcbesiege.2': ('第二の島：王の領地', "Island II: The King's Domain"),
    'objective.mcbesiege.targets': ('攻城目標を破壊 %s / %s', 'Destroy siege targets %s / %s'),
    'objective.mcbesiege.kill': ('敵を倒す %s / %s', 'Defeat enemies %s / %s'),
    'objective.mcbesiege.deliver': ('宝物を運ぶ %s / %s', 'Deliver the treasure %s / %s'),
    'objective.mcbesiege.reach': ('ゴールに到達 %s / %s', 'Reach the goal %s / %s'),
    'hud.mcbesiege.generating': ('戦場を生成中…', 'Generating…'),
    'hud.mcbesiege.build_keys': ('[%s] 起動/リセット　[%s] パーツ　[%s] ミラー　[%s] 操縦', '[%s] simulate  [%s] parts  [%s] mirror  [%s] control'),
    'title.mcbesiege.victory': ('攻城成功！', 'Siege successful!'),
    'tooltip.mcbesiege.wrench': ('右クリック: キー・設定　スニーク右クリック: 回転　空中: パレット',
                                 'Right-click: keys & settings  Sneak: rotate  In air: palette'),
    'tooltip.mcbesiege.connector': ('2つのパーツの面を順にクリックして接続（スニーク右クリックで取消）',
                                    'Click faces of two parts to connect them (sneak right-click cancels)'),
    'tooltip.mcbesiege.blueprint': ('始動ブロックを右クリックしてマシンを保存', 'Right-click a starting block to save the machine'),
    'tooltip.mcbesiege.blueprint_filled': ('%s パーツ — 地面を右クリックで建造', '%s parts — right-click the ground to build'),
    'entity.mcbesiege.machine': ('攻城兵器', 'Siege Machine'),
    'entity.mcbesiege.cannonball': ('砲弾', 'Cannonball'),
}


def main(assets_dir):
    ja, en = {}, {}
    for k, (jn, enn, jd, ed) in PARTS.items():
        ja[f'block.mcbesiege.{k}'] = jn
        en[f'block.mcbesiege.{k}'] = enn
        ja[f'desc.mcbesiege.{k}'] = jd
        en[f'desc.mcbesiege.{k}'] = ed
    for k, (jn, enn) in OTHER_BLOCKS.items():
        ja[f'block.mcbesiege.{k}'] = jn
        en[f'block.mcbesiege.{k}'] = enn
        ja[f'desc.mcbesiege.{k}'] = jn
        en[f'desc.mcbesiege.{k}'] = enn
    for k, (jn, enn, jd, ed) in ITEMS.items():
        ja[f'item.mcbesiege.{k}'] = jn
        en[f'item.mcbesiege.{k}'] = enn
        ja[f'desc.mcbesiege.{k}'] = jd
        en[f'desc.mcbesiege.{k}'] = ed
    for table, prefix in [(ACTIONS, 'action'), (SLIDERS, 'slider'), (TOGGLES, 'toggle'), (CATS, 'cat')]:
        for k, (jn, enn) in table.items():
            ja[f'{prefix}.mcbesiege.{k}'] = jn
            en[f'{prefix}.mcbesiege.{k}'] = enn
    for k, (jn, enn, jd, ed) in MISSIONS.items():
        ja[f'mission.mcbesiege.{k}'] = jn
        en[f'mission.mcbesiege.{k}'] = enn
        ja[f'mission.mcbesiege.{k}.desc'] = jd
        en[f'mission.mcbesiege.{k}.desc'] = ed
    for k, (jn, enn) in MISC.items():
        ja[k] = jn
        en[k] = enn
    os.makedirs(os.path.join(assets_dir, 'lang'), exist_ok=True)
    for name, table in [('ja_jp', ja), ('en_us', en)]:
        with open(os.path.join(assets_dir, 'lang', name + '.json'), 'w', encoding='utf-8') as f:
            json.dump(dict(sorted(table.items())), f, ensure_ascii=False, indent=1)
            f.write('\n')
