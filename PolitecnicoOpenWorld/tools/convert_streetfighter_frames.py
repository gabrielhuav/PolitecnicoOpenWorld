import ast, json, re, sys

SF = "/sessions/sleepy-charming-thompson/mnt/PolitecnicoOpenWorld/StreetFighter-main/StreetFighter-main/src/entitites/fighters"
OUT = "/sessions/sleepy-charming-thompson/mnt/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/DATA"

PUSH = {"IDLE": [-16,-80,32,78], "JUMP": [-16,-91,32,66], "BEND": [-16,-58,32,58], "CROUCH": [-16,-50,32,50]}
HURT = {
 "INVINCLIBLE": [[0,0,0,0],[0,0,0,0],[0,0,0,0]],
 "IDLE": [[-8,-88,24,16],[-26,-74,40,42],[-26,-31,40,32]],
 "BACKWARD": [[-19,-88,24,16],[-26,-74,40,42],[-26,-31,40,32]],
 "FORWARD": [[-3,-88,24,16],[-26,-74,40,42],[-26,-31,40,32]],
 "JUMP": [[-13,-106,28,18],[-26,-90,40,42],[-22,-66,38,18]],
 "BEND": [[-2,-68,24,18],[-16,-53,44,24],[-16,-24,44,24]],
 "CROUCH": [[6,-61,24,18],[-16,-46,44,24],[-16,-24,44,24]],
 "PUNCH": [[11,-94,24,18],[-7,-77,40,43],[-7,-33,40,33]],
}
STATES = {
 "IDLE":"idle","WALK_FORWARD":"walkForwards","WALK_BACKWARD":"walkBackwards","JUMP_START":"jumpStart",
 "JUMP_UP":"jumpUp","JUMP_FORWARD":"jumpForwards","JUMP_BACKWARD":"jumpBackwards","JUMP_LAND":"jumpLand",
 "CROUCH":"crouch","CROUCH_UP":"crouchUp","CROUCH_DOWN":"crouchDown","IDLE_TURN":"idleTurn",
 "CROUCH_TURN":"crouchTurn","LIGHT_PUNCH":"lightPunch","MEDIUM_PUNCH":"mediumPunch","HEAVY_PUNCH":"heavyPunch",
 "LIGHT_KICK":"lightKick","MEDIUM_KICK":"mediumKick","HEAVY_KICK":"heavyKick",
 "HURT_HEAD_LIGHT":"hurtHeadLight","HURT_HEAD_MEDIUM":"hurtHeadMedium","HURT_HEAD_HEAVY":"hurtHeadHeavy",
 "HURT_BODY_LIGHT":"hurtBodyLight","HURT_BODY_MEDIUM":"hurtBodyMedium","HURT_BODY_HEAVY":"hurtBodyHeavy",
 "SPECIAL_1_LIGHT":"special1Light","SPECIAL_1_MEDIUM":"special1Medium","SPECIAL_1_HEAVY":"special1Heavy",
 "VICTORY":"victory","KO":"ko",
}

def strip_comments(s):
    s = re.sub(r"//[^\n]*", "", s)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return s

def extract_block(src, start_marker, open_ch, close_ch):
    i = src.index(start_marker)
    i = src.index(open_ch, i)
    depth = 0
    for j in range(i, len(src)):
        if src[j] == open_ch: depth += 1
        elif src[j] == close_ch:
            depth -= 1
            if depth == 0:
                return src[i:j+1]
    raise ValueError("unbalanced")

def convert(fighter_file, out_file):
    src = open(fighter_file, encoding="utf-8").read()
    src = strip_comments(src)

    # ---- frames = new Map([ ... ]) ----
    fr = extract_block(src, "frames = new Map(", "[", "]")
    for k,v in PUSH.items(): fr = fr.replace("PushBox.%s" % k, json.dumps(v))
    for k,v in HURT.items(): fr = fr.replace("HurtBox.%s" % k, json.dumps(v))
    frames_list = ast.literal_eval(fr)
    frames = {}
    for key, data in frames_list:
        entry = {"src": data[0][0], "origin": data[0][1]}
        if len(data) > 1: entry["push"] = data[1]
        if len(data) > 2: entry["hurt"] = data[2]
        if len(data) > 3: entry["hit"] = data[3]
        frames[key] = entry

    # ---- animations = { ... } ----
    an = extract_block(src, "animations = {", "{", "}")
    an = re.sub(r"\[FighterState\.([A-Z0-9_]+)\]\s*:", lambda m: '"%s":' % STATES[m.group(1)], an)
    an = an.replace("FrameDelay.TRANSITION", "-1").replace("FrameDelay.FREEZE", "0")
    an = an.replace("FighterStruckDelay", "15")
    animations = ast.literal_eval(an)
    animations = {k: [[fk, d] for fk, d in v] for k, v in animations.items()}

    # sanity: toda animación referencia frames existentes
    missing = sorted({fk for v in animations.values() for fk,_ in v if fk not in frames})
    if missing: raise SystemExit("FRAMES FALTANTES en %s: %s" % (fighter_file, missing))

    json.dump({"frames": frames, "animations": animations}, open(out_file, "w"), separators=(",",":"))
    print("%s -> frames=%d animations=%d" % (out_file.split("/")[-1], len(frames), len(animations)))

convert(SF + "/Ryu.js", OUT + "/ryu.json")
convert(SF + "/Ken.js", OUT + "/ken.json")

# spot checks
ryu = json.load(open(OUT + "/ryu.json"))
assert ryu["frames"]["idle-1"]["src"] == [75,14,60,89], ryu["frames"]["idle-1"]
assert len(ryu["animations"]["idle"]) == 6
assert ryu["animations"]["crouch"] == [["crouch-3",-1]]
ken = json.load(open(OUT + "/ken.json"))
assert "lightPunch" in ken["animations"] and "special1Heavy" in ken["animations"]
lp = ken["frames"].get("light-punch-2"); print("ken light-punch-2:", lp)
print("OK")
