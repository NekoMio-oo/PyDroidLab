"""类与数据类模板（含 __repr__ / 比较 / 属性校验）

适合：把一组相关数据和操作组织在一起。
要点：@dataclass 能自动生成 __init__ / __repr__ / __eq__，写数据类首选。
"""

from dataclasses import dataclass, field, asdict


class Temperature:
    """普通类：带校验的属性 + 运算符重载。"""

    def __init__(self, celsius):
        self.celsius = celsius

    @property
    def celsius(self):
        return self._celsius

    @celsius.setter
    def celsius(self, value):
        if value < -273.15:
            raise ValueError(f"低于绝对零度：{value}")
        self._celsius = float(value)

    @property
    def fahrenheit(self):
        return self._celsius * 9 / 5 + 32

    @fahrenheit.setter
    def fahrenheit(self, value):
        self.celsius = (value - 32) * 5 / 9

    def __repr__(self):
        return f"Temperature({self._celsius:.2f}°C)"

    def __eq__(self, other):
        return isinstance(other, Temperature) and abs(self._celsius - other._celsius) < 1e-9

    def __add__(self, other):
        return Temperature(self._celsius + other._celsius)


@dataclass(order=True)
class Student:
    """数据类：自动生成构造器、repr、相等与排序。"""

    name: str
    score: int = 0
    tags: list = field(default_factory=list)   # 可变默认值必须用 field(default_factory=...)

    @property
    def passed(self):
        return self.score >= 60

    def to_dict(self):
        d = asdict(self)
        d["passed"] = self.passed
        return d


if __name__ == "__main__":
    t = Temperature(25)
    print(t, "->", f"{t.fahrenheit:.1f}°F")
    t.fahrenheit = 212
    print("212°F 换算回:", t)
    print("相加:", Temperature(10) + Temperature(15))
    print("相等:", Temperature(25) == Temperature(25.0))

    try:
        Temperature(-300)
    except ValueError as e:
        print("校验生效:", e)

    print()
    students = [
        Student("小明", 92, ["python"]),
        Student("小红", 88),
        Student("小刚", 57, ["c++"]),
    ]
    for s in sorted(students):          # order=True 让排序直接可用
        print(f"{s.name:<4} {s.score:>3}  {'及格' if s.passed else '不及格'}  {s.tags}")
    print("字典化:", students[0].to_dict())
