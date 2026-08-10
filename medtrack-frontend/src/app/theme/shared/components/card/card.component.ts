import { Component, ContentChild, ElementRef, TemplateRef, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './card.component.html',
  styleUrls: ['./card.component.scss']
})
export class CardComponent {
  cardTitle = input<string>();

  cardClass = input<string>();

  showContent = input(true);

  blockClass = input<string>();

  headerClass = input<string>();

  showHeader = input(true);

  padding = input(20);

  @ContentChild('headerOptionsTemplate') headerOptionsTemplate!: TemplateRef<ElementRef>;

  @ContentChild('headerTitleTemplate') headerTitleTemplate!: TemplateRef<ElementRef>;
}
