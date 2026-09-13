import { cn } from "@/lib/utils";
import type { AnswerStyle } from "@/types";

const OPTIONS: { value: AnswerStyle; label: string }[] = [
  { value: "formal", label: "正式" },
  { value: "casual", label: "口语" },
  { value: "concise", label: "简洁" }
];

interface AnswerStyleSelectorProps {
  value: AnswerStyle | null;
  onChange: (value: AnswerStyle | null) => void;
  disabled?: boolean;
}

/**
 * 回答风格选择器：无 / 正式 / 口语 / 简洁
 * 「无」= 不携带 answerStyle 参数，服务端保持默认行为
 */
export function AnswerStyleSelector({ value, onChange, disabled }: AnswerStyleSelectorProps) {
  const itemClass = (active: boolean) =>
    cn(
      "rounded-md px-2.5 py-1 text-xs font-medium transition-all",
      active ? "bg-[#DBEAFE] text-[#2563EB]" : "text-[#999999] hover:bg-[#F5F5F5]",
      disabled && "cursor-not-allowed opacity-60"
    );

  return (
    <div className="inline-flex items-center gap-0.5 rounded-lg border border-[#E5E5E5] bg-white p-0.5">
      <button
        type="button"
        onClick={() => onChange(null)}
        disabled={disabled}
        aria-pressed={value === null}
        className={itemClass(value === null)}
      >
        无
      </button>
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          disabled={disabled}
          aria-pressed={value === option.value}
          className={itemClass(value === option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
