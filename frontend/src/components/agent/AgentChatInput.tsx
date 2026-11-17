import { useEffect, useRef, useState } from "react";
import { Loader2, Paperclip, Send } from "lucide-react";
import { useAgentActions, useAgentState } from "../../agent/agentStore";
import { AttachmentBar } from "./AgentMessageItem";

interface Props {
  compact?: boolean;
  autoFocus?: boolean;
}

export default function AgentChatInput({ compact = false, autoFocus = false }: Props) {
  const { sending, uploading, attachment } = useAgentState();
  const { send, uploadFile, clearAttachment, draftRef } = useAgentActions();
  const [text, setText] = useState(() => draftRef.current);
  const areaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // 只在挂载时聚焦一次；不在每次 prop/state 变化时抢焦点
  useEffect(() => {
    if (autoFocus) areaRef.current?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = () => {
    const value = text.trim();
    if (!value || sending) return;
    draftRef.current = "";
    setText("");
    void send(value);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className={compact ? "border-t border-gray-100 p-2.5" : "border-t border-gray-100 p-4"}>
      {uploading && <AttachmentBar name="正在上传…" uploading onRemove={() => undefined} />}
      {attachment && !uploading && (
        <AttachmentBar
          name={attachment.filename}
          size={attachment.size}
          onRemove={clearAttachment}
        />
      )}

      <div className="flex items-end gap-1.5 rounded-xl border border-gray-200 px-2.5 py-1.5 transition focus-within:border-blue-400 focus-within:ring-2 focus-within:ring-blue-100">
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            void uploadFile(file).finally(() => {
              if (fileRef.current) fileRef.current.value = "";
            });
          }}
        />
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          disabled={uploading || sending}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-blue-600 disabled:opacity-50"
          title="上传图片（≤5MB，支持 JPG/PNG/WEBP/GIF/BMP）"
        >
          {uploading ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Paperclip className="h-4 w-4" />
          )}
        </button>

        <textarea
          ref={areaRef}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            draftRef.current = e.target.value;
          }}
          onKeyDown={onKeyDown}
          rows={compact ? 1 : 2}
          placeholder="描述你的任务，例如：我想识别图片中的物体"
          className="max-h-32 min-h-[32px] flex-1 resize-none bg-transparent text-sm text-gray-800 outline-none"
          disabled={sending}
        />

        <button
          type="button"
          onClick={submit}
          disabled={sending || !text.trim()}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white transition hover:bg-blue-700 disabled:bg-gray-300"
          title="发送（Enter）"
        >
          {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
        </button>
      </div>

      <p
        className={`mt-1.5 text-center text-[11px] text-gray-400 ${compact ? "hidden sm:block" : ""}`}
      >
        Enter 发送 · Shift + Enter 换行 · 推理需你确认后才会执行
      </p>
    </div>
  );
}
