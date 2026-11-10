"""Castle entry point: plateau works, viaduct, gatehouse, walls, palace and interiors."""
from . import exterior
from . import plan as P


def build(w, T):
    exterior.build(w, T)
    try:
        from . import interiors
    except ImportError:
        interiors = None
    if interiors is not None:
        interiors.build(w, T)
    for name, pos, yaw in (("story.lilia", (0, P.PY + 1, -120), 180.0), ("story.nox", (-44, P.TY + 1, -130), 90.0)):
        if name not in w.spots:
            w.spot(name, pos, yaw)
