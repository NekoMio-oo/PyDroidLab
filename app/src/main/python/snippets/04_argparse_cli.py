"""命令行参数解析（argparse）

适合：写一个能被「终端」页用 `python script.py --name xxx` 调用的工具。
要点：参数名用 -- 开头，help 文字写清楚，--flag 用 action="store_true"。
"""

import argparse
import sys


def build_parser():
    parser = argparse.ArgumentParser(
        prog="demo_cli",
        description="argparse 用法演示：统计 1..N 里满足条件的数",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("n", type=int, help="上界（包含）")
    parser.add_argument("-d", "--divisor", type=int, default=3,
                        help="筛除数：输出能被它整除的数")
    parser.add_argument("-l", "--limit", type=int, default=10,
                        help="最多输出多少行")
    parser.add_argument("--reverse", action="store_true",
                        help="是否从大到小输出")
    parser.add_argument("--sep", default=", ", help="输出分隔符")
    parser.add_argument("-v", "--verbose", action="store_true", help="打印调试信息")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)

    if args.verbose:
        print(f"[debug] 解析结果：{args}", file=sys.stderr)

    if args.n <= 0:
        print("n 必须是正整数", file=sys.stderr)
        return 2

    picked = [x for x in range(1, args.n + 1) if x % args.divisor == 0]
    if args.reverse:
        picked = picked[::-1]
    picked = picked[:args.limit]

    print(f"1..{args.n} 中能被 {args.divisor} 整除的数（最多 {args.limit} 个）：")
    print(args.sep.join(str(x) for x in picked))
    print(f"共 {len(picked)} 个")
    return 0


if __name__ == "__main__":
    # 终端里可以这样试：
    #   python 04_argparse_cli.py 50 --divisor 7 --limit 5
    #   python 04_argparse_cli.py 20 -d 4 --reverse -v
    print("=== 用默认参数运行 ===")
    main(["30"])

    print("\n=== 用自定义参数运行 ===")
    main(["50", "--divisor", "7", "--limit", "5"])

    print("\n=== 反向 + 调试输出 ===")
    main(["20", "-d", "4", "--reverse", "-v"])

    print("\n=== help 输出（截取前 12 行）===")
    try:
        build_parser().parse_args(["--help"])
    except SystemExit:
        pass
