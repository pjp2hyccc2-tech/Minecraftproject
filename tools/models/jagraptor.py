"""Jagraptor (ジャグラプトル) - blue pack raptor with orange stripes and a fin crest. Small monster."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'small'))
from smallkit import run  # noqa: E402
from raptor import build  # noqa: E402

m = build('jagraptor', 'jag')

if __name__ == '__main__':
    run(m)
