"""Castle entry point: plateau works, viaduct, gatehouse, walls, palace and interiors."""
from . import plan as P


def build(w, T):
    # provisional story spots (refined by the interior builders)
    w.spot("story.lilia", (0, P.PY + 1, -120), 180.0)
    w.spot("story.nox", (-44, P.PY + 1, -130), 90.0)
    w.spot("story.arcelia_knight_captain", (-74, P.PY + 1, -112), 180.0)
