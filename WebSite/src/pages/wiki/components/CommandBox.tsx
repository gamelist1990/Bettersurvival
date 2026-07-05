type CommandBoxProps = {
  command: string;
  description?: string;
  op?: boolean;
};

export function CommandBox({ command, description, op }: CommandBoxProps) {
  return (
    <div className="wiki-cmd">
      <div className="wiki-cmd-line">
        <span className="wiki-cmd-prompt">$</span>
        <code className="wiki-cmd-code">{command}</code>
        {op ? <span className="wiki-cmd-op">OP</span> : null}
      </div>
      {description ? <p className="wiki-cmd-desc">{description}</p> : null}
    </div>
  );
}
