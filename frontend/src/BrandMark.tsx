const marks = {
  bank: { source: "/brand/rshb.png", label: "РСХБ" },
  digital: { source: "/brand/rshb-digital.png", label: "РСХБ Цифра" },
};

export function BrandMark({
  variant,
}: Readonly<{ variant: keyof typeof marks }>) {
  const mark = marks[variant];
  return (
    <span className={"ecosystem-mark ecosystem-mark--" + variant}>
      <img
        src={mark.source}
        alt={mark.label}
        width={variant === "bank" ? 980 : 312}
        height={variant === "bank" ? 980 : 312}
      />
    </span>
  );
}
