"""Blizraptor (ブリザラプトル) - snow-white raptor with a frost crest (shares the Jagraptor skeleton). Small monster."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import run  # noqa: E402
from raptor import build  # noqa: E402

m = build('blizraptor', 'bliz')

if __name__ == '__main__':
    run(m)
