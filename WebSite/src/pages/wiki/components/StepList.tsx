type StepListProps = {
  steps: string[];
};

export function StepList({ steps }: StepListProps) {
  return (
    <ol className="wiki-steps">
      {steps.map((step, index) => (
        <li key={index} className="wiki-step">
          <span className="wiki-step-num">{index + 1}</span>
          <span className="wiki-step-text">{step}</span>
        </li>
      ))}
    </ol>
  );
}
