"""Translates the strict Java subset used in the model animation code (setupAnim bodies + static helpers) to
Python, so the offline previews run exactly the logic that ships in the Java models.

Supported: float/int/boolean locals, assignments (= += -= *= /=), if / else, blocks, return, expression
statements, ternaries, casts (int)/(float), && || !, comparisons, + - * / %, calls to Mth.* / Math.* / helpers,
field access (part.xRot), entity.pose() / entity.poseTime(x). Anything else raises (so the Java stays simple).
Numeric literals: a literal with a '.' must carry the f suffix (Java float) -> checked here too.
"""
import re

TOKEN = re.compile(r'\s*(?:(//[^\n]*)|(/\*.*?\*/)|(\d+\.\d*[fF]?|\d*\.\d+[fF]?|\d+[fF]?)|([A-Za-z_][A-Za-z_0-9]*)'
                   r'|(\+=|-=|\*=|/=|==|!=|<=|>=|&&|\|\||[-+*/%<>=!?:;,.(){}]))', re.S)

MAP_CALLS = {'Mth.sin': 'sin', 'Mth.cos': 'cos', 'Mth.abs': 'abs', 'Mth.clamp': 'clamp', 'Mth.lerp': 'lerp',
             'Mth.floor': 'floor_i', 'Mth.sqrt': 'sqrt', 'Mth.frac': 'frac', 'Math.min': 'min', 'Math.max': 'max',
             'Math.abs': 'abs', 'Mth.wrapDegrees': 'wrap_deg', 'Math.exp': 'exp', 'Math.signum': 'signum',
             'Math.atan2': 'atan2', 'Math.sqrt': 'sqrt', 'Math.pow': 'pow_'}
MAP_CONST = {'Mth.PI': 'PI', 'Mth.HALF_PI': 'HALF_PI', 'Mth.TWO_PI': 'TWO_PI'}
TYPES = ('float', 'int', 'boolean', 'ModelPart')


class JavaSyntax(Exception):
    pass


def tokenize(src):
    pos = 0
    out = []
    src = src.rstrip()
    while pos < len(src):
        m = TOKEN.match(src, pos)
        if not m or m.end() == pos:
            raise JavaSyntax(f'cannot tokenize near: {src[pos:pos + 40]!r}')
        pos = m.end()
        if m.group(1) or m.group(2):
            continue
        if m.group(3):
            out.append(('num', m.group(3)))
        elif m.group(4):
            out.append(('id', m.group(4)))
        elif m.group(5):
            out.append(('op', m.group(5)))
    return out


class Parser:
    def __init__(self, tokens):
        self.t = tokens
        self.i = 0

    def peek(self, k=0):
        return self.t[self.i + k] if self.i + k < len(self.t) else ('eof', '')

    def take(self, val=None):
        tok = self.peek()
        if val is not None and tok[1] != val:
            raise JavaSyntax(f'expected {val!r}, got {tok[1]!r} at token {self.i}: '
                             + ' '.join(x[1] for x in self.t[max(0, self.i - 8):self.i + 4]))
        self.i += 1
        return tok

    def at(self, val):
        return self.peek()[1] == val

    # ------------------------------------------------------------------ statements -> list of python lines
    def statements(self, ind, stop='}'):
        lines = []
        while not self.at(stop) and self.peek()[0] != 'eof':
            lines += self.statement(ind)
        return lines

    def statement(self, ind):
        pad = '    ' * ind
        tok = self.peek()
        if tok[1] == '{':
            self.take('{')
            body = self.statements(ind)
            self.take('}')
            return body or [pad + 'pass']
        if tok[1] == 'if':
            self.take('if')
            self.take('(')
            cond = self.expr()
            self.take(')')
            body = self.statement(ind + 1)
            lines = [pad + f'if {cond}:'] + (body or [pad + '    pass'])
            if self.at('else'):
                self.take('else')
                eb = self.statement(ind + 1)
                lines += [pad + 'else:'] + (eb or [pad + '    pass'])
            return lines
        if tok[1] == 'return':
            self.take('return')
            e = self.expr()
            self.take(';')
            return [pad + f'return {e}']
        if tok[1] == 'final':
            self.take('final')
            tok = self.peek()
        if tok[1] in TYPES and self.peek(1)[0] == 'id':
            typ = self.take()[1]
            lines = []
            while True:
                name = self.take()[1]
                if self.at('='):
                    self.take('=')
                    e = self.expr()
                else:
                    e = {'float': '0.0', 'int': '0', 'boolean': 'False', 'ModelPart': 'None'}[typ]
                conv = {'float': f'float({e})', 'int': f'jint({e})', 'boolean': f'bool({e})', 'ModelPart': e}[typ]
                lines.append(pad + f'{name} = {conv}')
                if self.at(','):
                    self.take(',')
                    continue
                break
            self.take(';')
            return lines
        # assignment or expression statement
        start = self.i
        lv = self.postfix()
        if self.peek()[1] in ('=', '+=', '-=', '*=', '/='):
            op = self.take()[1]
            e = self.expr()
            self.take(';')
            if op == '/=':
                return [pad + f'{lv} = jdiv({lv}, {e})']
            return [pad + f'{lv} {op} {e}']
        self.take(';')
        return [pad + lv]

    # ------------------------------------------------------------------ expressions -> python source
    def expr(self):
        c = self.orx()
        if self.at('?'):
            self.take('?')
            a = self.expr()
            self.take(':')
            b = self.expr()
            return f'({a} if {c} else {b})'
        return c

    def orx(self):
        e = self.andx()
        while self.at('||'):
            self.take()
            e = f'({e} or {self.andx()})'
        return e

    def andx(self):
        e = self.eq()
        while self.at('&&'):
            self.take()
            e = f'({e} and {self.eq()})'
        return e

    def eq(self):
        e = self.rel()
        while self.peek()[1] in ('==', '!='):
            op = self.take()[1]
            e = f'({e} {op} {self.rel()})'
        return e

    def rel(self):
        e = self.add()
        while self.peek()[1] in ('<', '>', '<=', '>='):
            op = self.take()[1]
            e = f'({e} {op} {self.add()})'
        return e

    def add(self):
        e = self.mul()
        while self.peek()[1] in ('+', '-'):
            op = self.take()[1]
            e = f'({e} {op} {self.mul()})'
        return e

    def mul(self):
        e = self.unary()
        while self.peek()[1] in ('*', '/', '%'):
            op = self.take()[1]
            r = self.unary()
            if op == '*':
                e = f'({e} * {r})'
            elif op == '/':
                e = f'jdiv({e}, {r})'
            else:
                e = f'jmod({e}, {r})'
        return e

    def unary(self):
        tok = self.peek()
        if tok[1] == '-':
            self.take()
            return f'(-{self.unary()})'
        if tok[1] == '+':
            self.take()
            return self.unary()
        if tok[1] == '!':
            self.take()
            return f'(not {self.unary()})'
        if tok[1] == '(' and self.peek(1)[1] in ('float', 'int') and self.peek(2)[1] == ')':
            self.take('(')
            typ = self.take()[1]
            self.take(')')
            v = self.unary()
            return f'float({v})' if typ == 'float' else f'jint({v})'
        return self.postfix()

    def postfix(self):
        e, dotted = self.primary()
        while self.at('.'):
            self.take('.')
            name = self.take()[1]
            dotted = f'{dotted}.{name}' if dotted else None
            if self.at('('):
                args = self.args()
                if dotted in MAP_CALLS:
                    e = f'{MAP_CALLS[dotted]}({args})'
                elif dotted and dotted.startswith(('Mth.', 'Math.')):
                    raise JavaSyntax(f'unsupported call {dotted}')
                else:
                    e = f'{e}.{name}({args})'
                dotted = None
            else:
                if dotted in MAP_CONST:
                    e = MAP_CONST[dotted]
                elif e in ('Mth', 'Math'):
                    raise JavaSyntax(f'unsupported constant {dotted}')
                else:
                    e = name if e == 'this' else f'{e}.{name}'
        return e

    def primary(self):
        tok = self.take()
        if tok[0] == 'num':
            s = tok[1]
            if '.' in s and not s[-1] in 'fF':
                raise JavaSyntax(f'double literal {s} (add f)')
            s = s.rstrip('fF')
            return (s if '.' in s or tok[1][-1] not in 'fF' else s + '.0'), None
        if tok[0] == 'id':
            name = tok[1]
            if name in ('true', 'false'):
                return ('True' if name == 'true' else 'False'), None
            if self.at('('):
                return f'{name}({self.args()})', None
            return name, name
        if tok[1] == '(':
            e = self.expr()
            self.take(')')
            return f'({e})', None
        raise JavaSyntax(f'unexpected token {tok}')

    def args(self):
        self.take('(')
        out = []
        while not self.at(')'):
            out.append(self.expr())
            if self.at(','):
                self.take(',')
        self.take(')')
        return ', '.join(out)


def translate_body(src, ind=1):
    p = Parser(tokenize(src))
    lines = p.statements(ind, stop='\x00')
    return lines


METHOD = re.compile(r'private\s+static\s+(float|int|boolean|void|ModelPart)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*\{', re.S)


def translate_helpers(src):
    """private static <type> name(<typed params>) { body } ... -> python defs"""
    out = []
    pos = 0
    while True:
        m = METHOD.search(src, pos)
        if not m:
            break
        depth, j = 1, m.end()
        while depth:
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
            j += 1
        body = src[m.end():j - 1]
        params = [p.strip().split()[-1] for p in m.group(3).split(',') if p.strip()]
        out.append(f'def {m.group(2)}({", ".join(params)}):')
        lines = translate_body(body, 1)
        out += lines or ['    pass']
        out.append('')
        pos = j
    return out


PRELUDE = '''
import math
PI = math.pi
HALF_PI = math.pi / 2
TWO_PI = math.pi * 2
_SIN = [math.sin(i * math.pi * 2.0 / 65536.0) for i in range(65536)]
def _toint(x):
    if x != x:
        return 0
    if x >= 2147483647.0:
        return 2147483647
    if x <= -2147483648.0:
        return -2147483648
    return int(x)
def sin(x): return _SIN[_toint(x * 10430.378) & 65535]
def cos(x): return _SIN[_toint(x * 10430.378 + 16384.0) & 65535]
def exp(x): return math.exp(max(-80.0, min(80.0, x)))
def signum(x): return 1.0 if x > 0 else (-1.0 if x < 0 else 0.0)
def atan2(a, b): return math.atan2(a, b)
def pow_(a, b): return math.pow(a, b)
def sqrt(x): return math.sqrt(max(0.0, x))
def clamp(v, lo, hi): return lo if v < lo else (hi if v > hi else v)
def lerp(d, a, b): return a + d * (b - a)
def floor_i(x): return int(math.floor(x))
def frac(x): return x - math.floor(x)
def wrap_deg(x):
    f = math.fmod(x, 360.0)
    if f >= 180.0: f -= 360.0
    if f < -180.0: f += 360.0
    return f
def jint(x): return int(x)
def jdiv(a, b):
    if isinstance(a, int) and isinstance(b, int) and not isinstance(a, bool):
        q = abs(a) // abs(b)
        return q if (a >= 0) == (b >= 0) else -q
    return a / b
def jmod(a, b):
    if isinstance(a, int) and isinstance(b, int):
        r = abs(a) % abs(b)
        return r if a >= 0 else -r
    return math.fmod(a, b)
'''


def compile_anim(body_src, helpers_src, part_names, consts, extra_globals=None):
    """Returns a python function setup(parts, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
    pose, pt, glitch, st, prev, partial) -> blend ticks, running the translated Java body."""
    lines = [PRELUDE]
    for k, v in consts.items():
        lines.append(f'{k} = {v}')
    lines += translate_helpers(helpers_src or '')
    lines.append('def setup_anim(__parts, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, '
                 'pose, pt, glitch, st, prev, partial):')
    for n in part_names:
        lines.append(f'    {n} = __parts[{n!r}]')
    lines.append('    blend = 8.0')
    lines += translate_body(body_src, 1)
    lines.append('    return blend')
    code = '\n'.join(lines)
    ns = dict(extra_globals or {})
    try:
        exec(compile(code, '<anim>', 'exec'), ns)
    except SyntaxError as e:
        raise JavaSyntax(f'translated python does not compile: {e}\n{code}')
    return ns['setup_anim'], code
